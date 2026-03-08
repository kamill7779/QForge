"""从数据库读取真实题目，生成 Word 文档验证渲染效果。

用法: cd backend/export-sidecar && python scripts/gen_test_doc.py
输出: ../../test_export_fixed.docx
"""
import sys, os, io, base64
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

import mysql.connector
from models.question import QuestionData, AnswerData, AssetData
from models.compose import ExportOptions
from assembler.document import DocumentAssembler
from parser.latex_engine import LatexEngine

DB_CONFIG = dict(host="localhost", port=3306, user="qforge", password="qforge", database="qforge")


def fetch_questions(limit=20):
    conn = mysql.connector.connect(**DB_CONFIG)
    cur = conn.cursor(dictionary=True)

    cur.execute(
        "SELECT id, question_uuid, stem_text, difficulty "
        "FROM q_question WHERE deleted=0 AND status='READY' "
        "ORDER BY updated_at DESC LIMIT %s",
        (limit,),
    )
    rows = cur.fetchall()
    questions = []

    for row in rows:
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
                if isinstance(raw, (bytes, bytearray)):
                    raw = base64.b64encode(raw).decode("ascii")
                qd.stem_assets[ar["ref_key"]] = AssetData(
                    ref_key=ar["ref_key"], image_data=raw,
                    mime_type=ar["mime_type"] or "image/png",
                )

        # answers
        cur.execute(
            "SELECT id, answer_uuid, latex_text, sort_order "
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
                        ref_key=aa["ref_key"], image_data=raw,
                        mime_type=aa["mime_type"] or "image/png",
                    )
            qd.answers.append(ad)
        questions.append(qd)

    cur.close()
    conn.close()
    return questions


def main():
    print("Fetching questions from DB...")
    questions = fetch_questions(20)
    print(f"Got {len(questions)} questions")

    engine = LatexEngine()
    print(f"LaTeX engine available: {engine.available}")

    asm = DocumentAssembler(engine)
    opts = ExportOptions(include_answers=True)

    buf = io.BytesIO()
    asm.build_questions(questions, buf, options=opts, title="导出渲染测试 (修复版)")
    buf.seek(0)

    out_path = os.path.join(os.path.dirname(__file__), "..", "..", "..", "test_export_fixed.docx")
    out_path = os.path.normpath(out_path)
    with open(out_path, "wb") as f:
        f.write(buf.read())
    print(f"Saved: {out_path} ({os.path.getsize(out_path)} bytes)")


if __name__ == "__main__":
    main()
