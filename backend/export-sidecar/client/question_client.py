"""
question-service HTTP 客户端 — 获取题目/组卷数据。

正式模式: 调用 question-service 内部 API (HTTP)
独立测试模式 (STANDALONE_MODE=true): 直连 MySQL 获取数据
"""
from __future__ import annotations

import base64
from typing import Dict, List, Optional

import httpx

import config
from models.question import AssetData, AnswerData, QuestionData
from models.compose import ComposeData, ComposeSection, ComposeItem


class QuestionClient:
    """统一数据获取接口。"""

    def __init__(self):
        self._standalone = config.STANDALONE_MODE
        self._base_url = config.QUESTION_SERVICE_URL

    # ══════════════════════════════════════════════════
    # 公开 API
    # ══════════════════════════════════════════════════

    async def fetch_questions_by_uuids(
        self, uuids: List[str], user: str = ""
    ) -> Dict[str, QuestionData]:
        """按 UUID 列表批量获取题目完整数据。"""
        if self._standalone:
            return self._db_fetch_questions(uuids)
        return await self._http_fetch_questions(uuids, user)

    async def fetch_compose_for_export(
        self, compose_uuid: str, user: str = ""
    ) -> Optional[ComposeData]:
        """获取组卷完整结构。"""
        if self._standalone:
            return self._db_fetch_compose(compose_uuid, user)
        return await self._http_fetch_compose(compose_uuid, user)

    # ══════════════════════════════════════════════════
    # HTTP 模式 — 调用 question-service 内部 API
    # ══════════════════════════════════════════════════

    async def _http_fetch_questions(
        self, uuids: List[str], user: str
    ) -> Dict[str, QuestionData]:
        async with httpx.AsyncClient(timeout=30.0) as client:
            resp = await client.post(
                f"{self._base_url}/internal/questions-export",
                json={"questionUuids": uuids},
                headers={"X-Auth-User": user},
            )
            resp.raise_for_status()
            data = resp.json()

        result: Dict[str, QuestionData] = {}
        for item in data:
            qd = self._parse_question_json(item)
            result[qd.question_uuid] = qd
        return result

    async def _http_fetch_compose(
        self, compose_uuid: str, user: str
    ) -> Optional[ComposeData]:
        async with httpx.AsyncClient(timeout=30.0) as client:
            resp = await client.get(
                f"{self._base_url}/internal/compose-export/{compose_uuid}",
                headers={"X-Auth-User": user},
            )
            if resp.status_code == 404:
                return None
            resp.raise_for_status()
            data = resp.json()

        return self._parse_compose_json(data)

    @staticmethod
    def _parse_question_json(item: dict) -> QuestionData:
        assets: Dict[str, AssetData] = {}
        for a in item.get("assets", []):
            ref = a.get("refKey", "")
            if ref:
                assets[ref] = AssetData(
                    ref_key=ref,
                    image_data=a.get("imageData", ""),
                    mime_type=a.get("mimeType", "image/png"),
                )

        answers = []
        for ans in item.get("answers", []):
            ans_assets: Dict[str, AssetData] = {}
            for aa in ans.get("assets", []):
                ref = aa.get("refKey", "")
                if ref:
                    ans_assets[ref] = AssetData(
                        ref_key=ref,
                        image_data=aa.get("imageData", ""),
                        mime_type=aa.get("mimeType", "image/png"),
                    )
            answers.append(AnswerData(
                answer_uuid=ans.get("answerUuid", ""),
                latex_text=ans.get("latexText", ""),
                sort_order=ans.get("sortOrder", 1),
                assets=ans_assets,
            ))

        return QuestionData(
            question_uuid=item.get("questionUuid", ""),
            stem_xml=item.get("stemText", ""),
            difficulty=item.get("difficulty"),
            answers=answers,
            stem_assets=assets,
            main_tags=item.get("mainTags", []),
            secondary_tags=item.get("secondaryTags", []),
        )

    @staticmethod
    def _parse_compose_json(data: dict) -> ComposeData:
        sections = []
        for s in data.get("sections", []):
            items = []
            for it in s.get("items", []):
                items.append(ComposeItem(
                    item_id=it.get("itemId", 0),
                    sort_order=it.get("sortOrder", 0),
                    question_uuid=it.get("questionUuid", ""),
                ))
            sections.append(ComposeSection(
                section_id=s.get("sectionId", 0),
                section_title=s.get("sectionTitle", ""),
                sort_order=s.get("sortOrder", 0),
                items=items,
            ))
        return ComposeData(
            compose_uuid=data.get("composeUuid", ""),
            title=data.get("title", "未命名试卷"),
            description=data.get("description"),
            total_questions=data.get("totalQuestions", 0),
            sections=sections,
        )

    # ══════════════════════════════════════════════════
    # 独立测试模式 — 直连 MySQL (不依赖 question-service)
    # ══════════════════════════════════════════════════

    def _db_fetch_questions(self, uuids: List[str]) -> Dict[str, QuestionData]:
        import mysql.connector

        conn = mysql.connector.connect(**config.DB_CONFIG)
        cur = conn.cursor(dictionary=True)
        result: Dict[str, QuestionData] = {}

        for uuid in uuids:
            cur.execute(
                "SELECT id, question_uuid, stem_text, status, difficulty "
                "FROM q_question WHERE question_uuid=%s AND deleted=0",
                (uuid,),
            )
            row = cur.fetchone()
            if not row:
                continue

            q_id = row["id"]
            qd = QuestionData(
                question_uuid=row["question_uuid"],
                stem_xml=row["stem_text"] or "",
                difficulty=row.get("difficulty"),
            )

            # stem assets
            cur.execute(
                "SELECT ref_key, image_data, mime_type "
                "FROM q_question_asset WHERE question_id=%s AND deleted=0",
                (q_id,),
            )
            for ar in cur.fetchall():
                if ar["ref_key"]:
                    raw = ar["image_data"] or ""
                    # 如果是 bytes，base64 编码
                    if isinstance(raw, (bytes, bytearray)):
                        raw = base64.b64encode(raw).decode("ascii")
                    qd.stem_assets[ar["ref_key"]] = AssetData(
                        ref_key=ar["ref_key"],
                        image_data=raw,
                        mime_type=ar["mime_type"] or "image/png",
                    )

            # answers
            cur.execute(
                "SELECT id, answer_uuid, answer_type, latex_text, sort_order "
                "FROM q_answer WHERE question_id=%s AND deleted=0 ORDER BY sort_order",
                (q_id,),
            )
            for ans_row in cur.fetchall():
                ad = AnswerData(
                    answer_uuid=ans_row["answer_uuid"],
                    latex_text=ans_row["latex_text"] or "",
                    sort_order=ans_row["sort_order"],
                )
                cur.execute(
                    "SELECT ref_key, image_data, mime_type "
                    "FROM q_answer_asset WHERE answer_id=%s AND deleted=0",
                    (ans_row["id"],),
                )
                for aa in cur.fetchall():
                    if aa["ref_key"]:
                        raw = aa["image_data"] or ""
                        if isinstance(raw, (bytes, bytearray)):
                            raw = base64.b64encode(raw).decode("ascii")
                        ad.assets[aa["ref_key"]] = AssetData(
                            ref_key=aa["ref_key"],
                            image_data=raw,
                            mime_type=aa["mime_type"] or "image/png",
                        )
                qd.answers.append(ad)
            result[uuid] = qd

        cur.close()
        conn.close()
        return result

    def _db_fetch_compose(
        self, compose_uuid: str, user: str
    ) -> Optional[ComposeData]:
        """独立模式暂不支持组卷查询 (组卷表可能不存在)，返回 None。"""
        return None

    def list_all_question_uuids(self, limit: int = 50) -> List[str]:
        """独立测试专用 — 列出数据库中所有题目 UUID。"""
        if not self._standalone:
            return []
        import mysql.connector

        conn = mysql.connector.connect(**config.DB_CONFIG)
        cur = conn.cursor()
        cur.execute(
            "SELECT question_uuid FROM q_question "
            "WHERE deleted=0 AND status='READY' "
            "ORDER BY updated_at DESC LIMIT %s",
            (limit,),
        )
        uuids = [row[0] for row in cur.fetchall()]
        cur.close()
        conn.close()
        return uuids
