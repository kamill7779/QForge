"""
export-sidecar — FastAPI 入口

端口: 8092
注册到 Nacos，提供 Word 导出 REST API。

架构: question-service 组装完整题目数据后调用本服务的内部接口，
      本服务是纯粹的渲染服务，不反向调用 question-service。
"""
# 注意: 不能使用 from __future__ import annotations，
# 否则 Pydantic V2 无法在运行时解析 Annotated 元数据。

import io
import logging
import traceback
from contextlib import asynccontextmanager
from typing import Dict, List, Optional

from fastapi import FastAPI, HTTPException
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, ConfigDict, Field


def _to_camel(name: str) -> str:
    parts = name.split("_")
    return parts[0] + "".join(w.capitalize() for w in parts[1:])

import config
from parser.latex_engine import LatexEngine
from parser.stem_parser import parse_stem_xml, parse_answer_xml
from renderer.context import RenderContext
from renderer.registry import render_node
from assembler.document import DocumentAssembler
from models.question import AssetData, AnswerData, QuestionData
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
assembler = DocumentAssembler(latex_engine)


# ─── 请求/响应模型 ─────────────────────────────────

class AssetPayload(BaseModel):
    """资产数据（图片 base64 + refKey）。"""
    model_config = ConfigDict(
        alias_generator=_to_camel, populate_by_name=True,
    )
    ref_key: str
    image_data: str
    mime_type: str = "image/png"


class AnswerPayload(BaseModel):
    """答案数据。"""
    model_config = ConfigDict(
        alias_generator=_to_camel, populate_by_name=True,
    )
    answer_uuid: str
    latex_text: str
    sort_order: int = 1
    assets: List[AssetPayload] = Field(default_factory=list)


class QuestionPayload(BaseModel):
    """完整题目数据（由 question-service 组装后传入）。"""
    model_config = ConfigDict(
        alias_generator=_to_camel, populate_by_name=True,
    )
    question_uuid: str
    stem_text: str
    difficulty: Optional[float] = None
    answers: List[AnswerPayload] = Field(default_factory=list)
    stem_assets: List[AssetPayload] = Field(default_factory=list)
    main_tags: List[dict] = Field(default_factory=list)
    secondary_tags: List[str] = Field(default_factory=list)


class SectionDefPayload(BaseModel):
    """自定义分区定义。"""
    model_config = ConfigDict(
        alias_generator=_to_camel, populate_by_name=True,
    )
    title: str
    question_uuids: List[str] = Field(min_length=1)


class InternalExportRequest(BaseModel):
    """内部导出请求 — 包含完整题目数据，无需再反向查询。"""
    model_config = ConfigDict(
        alias_generator=_to_camel, populate_by_name=True,
    )
    questions: List[QuestionPayload]
    title: Optional[str] = "题目导出"
    include_answers: bool = True
    answer_position: str = "AFTER_ALL"
    sections: Optional[List[SectionDefPayload]] = None


# ─── 工具函数 ──────────────────────────────────────

def _make_options(include_answers: bool, answer_position: str) -> ExportOptions:
    pos = AnswerPosition.AFTER_ALL
    if answer_position.upper() == "AFTER_EACH_QUESTION":
        pos = AnswerPosition.AFTER_EACH_QUESTION
    return ExportOptions(include_answers=include_answers, answer_position=pos)


def _docx_stream_response(buf: io.BytesIO, filename: str) -> StreamingResponse:
    buf.seek(0)
    from urllib.parse import quote
    # RFC 5987: 用 filename* 传递 UTF-8 编码的文件名
    safe_filename = quote(filename, safe="")
    return StreamingResponse(
        buf,
        media_type="application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        headers={
            "Content-Disposition": f"attachment; filename*=UTF-8''{safe_filename}",
            "Content-Length": str(buf.getbuffer().nbytes),
        },
    )


def _payload_to_question_data(q: QuestionPayload) -> QuestionData:
    """将请求 payload 转换为内部 QuestionData 数据模型。"""
    stem_assets: Dict[str, AssetData] = {}
    for a in q.stem_assets:
        if a.ref_key:
            stem_assets[a.ref_key] = AssetData(
                ref_key=a.ref_key,
                image_data=a.image_data,
                mime_type=a.mime_type,
            )

    answers = []
    for ans in q.answers:
        ans_assets: Dict[str, AssetData] = {}
        for aa in ans.assets:
            if aa.ref_key:
                ans_assets[aa.ref_key] = AssetData(
                    ref_key=aa.ref_key,
                    image_data=aa.image_data,
                    mime_type=aa.mime_type,
                )
        answers.append(AnswerData(
            answer_uuid=ans.answer_uuid,
            latex_text=ans.latex_text,
            sort_order=ans.sort_order,
            assets=ans_assets,
        ))

    return QuestionData(
        question_uuid=q.question_uuid,
        stem_xml=q.stem_text,
        difficulty=q.difficulty,
        answers=answers,
        stem_assets=stem_assets,
        main_tags=q.main_tags,
        secondary_tags=q.secondary_tags,
    )


# ─── 端点 ─────────────────────────────────────────

@app.get("/api/export/health")
async def health():
    return {
        "status": "UP",
        "service": config.SERVICE_NAME,
    }


@app.post("/internal/export/questions/word")
async def internal_export_questions_word(req: InternalExportRequest):
    """内部接口: question-service 组装完整数据后调用，直接渲染 Word。

    支持两种模式:
    1. flat: questions 列表按顺序编号
    2. sections: questions + sections 按分区编号
    """
    if not req.questions:
        raise HTTPException(status_code=400, detail="questions 不能为空")

    if len(req.questions) > config.MAX_QUESTIONS_PER_EXPORT:
        raise HTTPException(
            status_code=400,
            detail=f"最多支持导出 {config.MAX_QUESTIONS_PER_EXPORT} 题",
        )

    # 转换为内部数据模型
    questions_map: Dict[str, QuestionData] = {}
    for q in req.questions:
        qd = _payload_to_question_data(q)
        questions_map[qd.question_uuid] = qd

    options = _make_options(req.include_answers, req.answer_position)
    buf = io.BytesIO()

    try:
        if req.sections:
            section_defs = [
                ExportSectionDef(title=s.title, question_uuids=list(s.question_uuids))
                for s in req.sections
            ]
            assembler.build_questions(
                [], buf, options=options,
                title=req.title or "题目导出",
                sections=section_defs,
                questions_map=questions_map,
            )
        else:
            ordered = [questions_map[q.question_uuid] for q in req.questions
                       if q.question_uuid in questions_map]
            assembler.build_questions(
                ordered, buf, options=options,
                title=req.title or "题目导出",
            )
    except Exception as e:
        logger.error("build_questions failed: %s", traceback.format_exc())
        raise HTTPException(status_code=500, detail=f"生成文档失败: {e}")

    return _docx_stream_response(buf, f"{req.title or '题目导出'}.docx")


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
