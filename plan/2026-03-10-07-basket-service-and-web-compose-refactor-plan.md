# Basket Service And Web Compose Refactor

更新时间：2026-03-10

## 已实施范围

1. 新增 `question-basket-service`，接管 `/api/question-basket/**`。
2. 新增确认前组卷表：
   - `q_basket_compose`
   - `q_basket_compose_section`
   - `q_basket_compose_question`
3. `exam-service` 删除公开 `from-basket` 入口，新增内部 `from-basket-compose` 入口。
4. `/web` 中 `/compose` 改为确认前组卷页，移除左侧题库列表。
5. `/web` 新增 `/exams/:id/edit`，作为真实试卷编辑页。

## 当前事实

1. 用户在确认组卷前不会创建真实试卷。
2. 确认组卷后，真实试卷一次性写入 `q_exam_paper / q_exam_section / q_exam_question`。
3. 确认成功后会清空当前试题篮与确认前组卷状态。
4. 已生成试卷不会再受后续试题篮变化影响。

## 剩余验证

1. 需要补服务编译和前端构建级验证。
2. 需要做真实联调，确认新 Nacos 配置和网关路由已同步上线。
3. 需要补回归检查：
   - 题库选题进篮
   - 进入 `/compose`
   - 确认组卷生成真实试卷
   - `/exams/:id/edit`
   - `/preview/:id`
   - 导出 Word
