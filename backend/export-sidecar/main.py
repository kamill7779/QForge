"""
export-sidecar — FastAPI 入口

端口: 8092
注册到 Nacos，提供 Word 导出 REST API。
"""
# 注意: 不能使用 from __future__ import annotations，
# 否则 Pydantic V2 无法在运行时解析 Annotated 元数据。

import io
import logging
import traceback
from contextlib import asynccontextmanager
from typing import Annotated, List, Optional

from fastapi import FastAPI, HTTPException, Header
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, ConfigDict, Field


def _to_camel(name: str) -> str:
    parts = name.split("_")
    return parts[0] + "".join(w.capitalize() for w in parts[1:])

import config
from client.question_client import QuestionClient
from parser.latex_engine import LatexEngine
from parser.stem_parser import parse_stem_xml, parse_answer_xml
from renderer.context import RenderContext
from renderer.registry import render_node
from assembler.document import DocumentAssembler
from models.compose import AnswerPosition, ExportOptions, ExportSectionDef

# ─── logging ───────────────────────────────────────
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s - %(message)s",
)
logger = logging.getLogger("export-sidecar")

# ─── Nacos helpers ─────────────────────────────────

_nacos_client = None


def _init_nacos():
    """尝试注册到 Nacos，失败不影响启动。"""
    global _nacos_client
    try:
        import nacos
        _nacos_client = nacos.NacosClient(
            config.NACOS_SERVER, namespace="", username="nacos", password="nacos"
        )
        _nacos_client.add_naming_instance(
            config.SERVICE_NAME,
            config.SERVICE_IP,
            config.SERVICE_PORT,
            metadata={"preserved.register.source": "PYTHON"},
        )
        logger.info(
            "Nacos registration OK → %s:%s",
            config.SERVICE_IP,
            config.SERVICE_PORT,
        )
    except Exception as e:
        logger.warning("Nacos registration failed (service still runs): %s", e)


def _deregister_nacos():
    global _nacos_client
    if _nacos_client is None:
        return
    try:
        _nacos_client.remove_naming_instance(
            config.SERVICE_NAME, config.SERVICE_IP, config.SERVICE_PORT
        )
        logger.info("Nacos deregistration OK")
    except Exception:
        pass


# ─── FastAPI lifespan ──────────────────────────────

@asynccontextmanager
async def lifespan(_app: FastAPI):
    _init_nacos()
    yield
    _deregister_nacos()


app = FastAPI(
    title="QForge Export Sidecar",
    version="0.1.0",
    lifespan=lifespan,
)

# ─── 共享实例 ──────────────────────────────────────

latex_engine = LatexEngine()
question_client = QuestionClient()
assembler = DocumentAssembler(latex_engine)


# ─── 请求/响应模型 ─────────────────────────────────

class SectionDefRequest(BaseModel):
    model_config = ConfigDict(
        alias_generator=_to_camel, populate_by_name=True,
    )
    title: str
    question_uuids: List[str] = Field(min_length=1)


class QuestionsExportRequest(BaseModel):
    model_config = ConfigDict(
        alias_generator=_to_camel, populate_by_name=True,
    )

    question_uuids: List[str] = Field(default_factory=list)
    title: Optional[str] = "题目导出"
    include_answers: bool = True
    answer_position: str = "AFTER_ALL"
    sections: Optional[List[SectionDefRequest]] = None


class ComposeExportRequest(BaseModel):
    model_config = ConfigDict(
        alias_generator=_to_camel, populate_by_name=True,
    )

    include_answers: bool = True
    answer_position: str = "AFTER_ALL"


# ─── 工具函数 ──────────────────────────────────────

def _make_options(include_answers: bool, answer_position: str) -> ExportOptions:
    pos = AnswerPosition.AFTER_ALL
    if answer_position.upper() == "AFTER_EACH_QUESTION":
        pos = AnswerPosition.AFTER_EACH_QUESTION
    return ExportOptions(include_answers=include_answers, answer_position=pos)


def _docx_stream_response(buf: io.BytesIO, filename: str) -> StreamingResponse:
    buf.seek(0)
    return StreamingResponse(
        buf,
        media_type="application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        headers={
            "Content-Disposition": f'attachment; filename="{filename}"',
            "Content-Length": str(buf.getbuffer().nbytes),
        },
    )


# ─── 端点 ─────────────────────────────────────────

@app.get("/api/export/health")
async def health():
    return {
        "status": "UP",
        "service": config.SERVICE_NAME,
    }


@app.post("/api/export/questions/word")
async def export_questions_word(
    req: QuestionsExportRequest,
    x_auth_user: Optional[str] = Header(None, alias="X-Auth-User"),
):
    """批量导出题目为 Word 文档。

    支持两种模式:
    1. flat: 提供 questionUuids → 平铺编号
    2. sections: 提供 sections=[{title, questionUuids}] → 分区编号
    两者不能同时为空。
    """
    # 收集全部 UUID
    section_defs: list[ExportSectionDef] | None = None
    if req.sections:
        all_uuids = []
        section_defs = []
        for s in req.sections:
            all_uuids.extend(s.question_uuids)
            section_defs.append(
                ExportSectionDef(title=s.title, question_uuids=list(s.question_uuids))
            )
    elif req.question_uuids:
        all_uuids = list(req.question_uuids)
    else:
        raise HTTPException(status_code=400, detail="questionUuids 和 sections 不能同时为空")

    if len(all_uuids) > config.MAX_QUESTIONS_PER_EXPORT:
        raise HTTPException(
            status_code=400,
            detail=f"最多支持导出 {config.MAX_QUESTIONS_PER_EXPORT} 题",
        )

    try:
        questions_map = await question_client.fetch_questions_by_uuids(
            all_uuids, user=x_auth_user or ""
        )
    except Exception as e:
        logger.error("fetch_questions failed: %s", traceback.format_exc())
        raise HTTPException(status_code=502, detail=f"获取题目数据失败: {e}")

    if not questions_map:
        raise HTTPException(status_code=404, detail="未找到任何题目")

    options = _make_options(req.include_answers, req.answer_position)
    buf = io.BytesIO()
    try:
        if section_defs:
            assembler.build_questions(
                [], buf, options=options,
                title=req.title or "题目导出",
                sections=section_defs,
                questions_map=questions_map,
            )
        else:
            ordered = [questions_map[u] for u in all_uuids if u in questions_map]
            assembler.build_questions(
                ordered, buf, options=options,
                title=req.title or "题目导出",
            )
    except Exception as e:
        logger.error("build_questions failed: %s", traceback.format_exc())
        raise HTTPException(status_code=500, detail=f"生成文档失败: {e}")

    return _docx_stream_response(buf, f"{req.title or '题目导出'}.docx")


@app.post("/api/export/compose/{compose_uuid}/word")
async def export_compose_word(
    compose_uuid: str,
    req: ComposeExportRequest = None,
    x_auth_user: Optional[str] = Header(None, alias="X-Auth-User"),
):
    """导出组卷为 Word 文档。"""
    if req is None:
        req = ComposeExportRequest()

    try:
        compose = await question_client.fetch_compose_for_export(
            compose_uuid, user=x_auth_user or ""
        )
    except Exception as e:
        logger.error("fetch_compose failed: %s", traceback.format_exc())
        raise HTTPException(status_code=502, detail=f"获取组卷数据失败: {e}")

    if compose is None:
        raise HTTPException(status_code=404, detail="组卷不存在或无权访问")

    # 收集所有题目 UUID
    all_uuids = []
    for sec in compose.sections:
        for item in sec.items:
            all_uuids.append(item.question_uuid)

    if not all_uuids:
        raise HTTPException(status_code=400, detail="组卷中没有题目")

    try:
        questions_map = await question_client.fetch_questions_by_uuids(
            all_uuids, user=x_auth_user or ""
        )
    except Exception as e:
        logger.error("fetch_questions for compose failed: %s", traceback.format_exc())
        raise HTTPException(status_code=502, detail=f"获取题目数据失败: {e}")

    options = _make_options(req.include_answers, req.answer_position)
    buf = io.BytesIO()
    try:
        assembler.build_compose(compose, questions_map, buf, options=options)
    except Exception as e:
        logger.error("build_compose failed: %s", traceback.format_exc())
        raise HTTPException(status_code=500, detail=f"生成文档失败: {e}")

    filename = f"{compose.title or '试卷'}.docx"
    return _docx_stream_response(buf, filename)


# ─── 入口 ─────────────────────────────────────────

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        "main:app",
        host="0.0.0.0",
        port=config.SERVICE_PORT,
        reload=False,
        log_level="info",
    )
