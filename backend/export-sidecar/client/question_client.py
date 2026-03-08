"""
question-service HTTP 客户端 — 获取题目/组卷数据。

通过 HTTP 调用 question-service 内部 API 获取数据。
"""
from __future__ import annotations

from typing import Dict, List, Optional

import httpx

import config
from models.question import AssetData, AnswerData, QuestionData
from models.compose import ComposeData, ComposeSection, ComposeItem


class QuestionClient:
    """统一数据获取接口 — 仅通过 HTTP 调用 question-service。"""

    def __init__(self):
        self._base_url = config.QUESTION_SERVICE_URL

    # ══════════════════════════════════════════════════
    # 公开 API
    # ══════════════════════════════════════════════════

    async def fetch_questions_by_uuids(
        self, uuids: List[str], user: str = ""
    ) -> Dict[str, QuestionData]:
        """按 UUID 列表批量获取题目完整数据。"""
        return await self._http_fetch_questions(uuids, user)

    async def fetch_compose_for_export(
        self, compose_uuid: str, user: str = ""
    ) -> Optional[ComposeData]:
        """获取组卷完整结构。"""
        return await self._http_fetch_compose(compose_uuid, user)

    # ══════════════════════════════════════════════════
    # HTTP — 调用 question-service 内部 API
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
