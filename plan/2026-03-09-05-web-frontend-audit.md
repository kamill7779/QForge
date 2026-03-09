# Web 前端静态审计

更新时间：2026-03-09

## 范围与边界

- 范围：`web/src` 全量静态代码，结合当前后端真实接口与 `client` 现有职责分工。
- 本次没有执行浏览器联调、自动化测试或构建。
- 已确认边界：
  - `client` 负责 OCR、AI 识别、试卷解析。
  - `web` 负责题库浏览、试题篮、组卷、预览、导出。
  - `export-sidecar` 由 `exam-service` 内部编排调用，`web` 不应直连 sidecar。

## 已在本轮实现的修正

1. 题库已经接入后端分页入口 `GET /api/questions/page`，并落成“后端 100 / 前端 20”的缓冲分页模型。
2. 单题详情已经接入 `GET /api/questions/{questionUuid}`，不再要求题库先全量加载。
3. `ExamComposeView` 的标题、副标题、时长编辑已经改为统一走 `examStore.updateExamMeta()`。
4. 组卷页已经补上“当前大题”选中态，左侧题目不再默认总是落到最后一个大题。
5. 无试卷时左侧点题的空引用风险已改为受控 `await createNew()` 流程。
6. `AI apply` 的请求路径已改为 `PUT /api/questions/{questionUuid}/ai-tasks/{taskUuid}/apply`。
7. Web 导出已切到“`web -> exam-service -> export-sidecar`”链路，并保留服务端返回的文件名。
8. 登出和 401 场景已经补上 `examStore`、`basketStore` 清理。
9. 试题篮图片加载已从“全量并发”改成首批加载 + 滚动分批补载。

## 当前剩余一级问题

### 1. 题库过滤与搜索仍以“已加载缓冲数据”为范围

位置：

- `web/src/stores/question.ts`
- `web/src/views/BankView.vue`
- `web/src/views/ExamComposeView.vue`

问题：

- 当前分页策略已经把首屏压力降下来了，但筛选树、关键字搜索和计数仍建立在本地已加载数据上。
- 在题量足够大时，用户如果还没滚到后面的批次，本地筛选结果并不代表全库结果。

影响：

- 当前实现解决了性能和交互卡顿问题，但还没有解决“全库查询语义正确性”。

结论：

- 下一步应推动后端分页接口继续支持 `keyword/filter` 查询条件，而不是让前端在缓冲数据上模拟全库搜索。

### 2. 题库统计文案还没有把“已加载数量”和“总数量”表达清楚

位置：

- `web/src/views/BankView.vue`
- `web/src/stores/question.ts`

问题：

- 页面目前展示 `filtered / total`，但 `filtered` 是当前已加载集合下的过滤结果，`total` 是后端总量。
- 对用户来说，这会天然带有“过滤结果覆盖全库”的误导。

影响：

- 题库变大后，用户会误读筛选结果和分页状态。

结论：

- 需要把文案改成“已加载 / 总量”或“当前缓冲命中数 / 全库总数”，并给出“继续加载更多”提示。

### 3. 题库页与组卷页仍缺少真正的响应式断点设计

位置：

- `web/src/views/BankView.vue`
- `web/src/views/ExamComposeView.vue`
- `web/src/views/AppShell.vue`

问题：

- 现在最致命的功能问题已修，但布局仍然偏桌面宽屏假设。
- 侧栏固定宽度、顶部 tab 密集、组卷页左右双栏在中窄宽度下仍会挤压。

影响：

- 宽度收窄后仍可能出现覆盖、拥挤或操作区难用。

结论：

- 下一轮应该把 `1280px / 1024px / 768px` 三档断点真正做完，而不是继续只靠弹性布局硬撑。

## 当前剩余二级问题

### 4. 试题篮图片虽然已分批加载，但还没有做到可视区精确懒加载

位置：

- `web/src/views/BasketView.vue`
- `web/src/composables/useQuestionAssets.ts`

问题：

- 当前已从“全量并发”降到批量加载，但触发条件仍是首批进入和滚动接近底部。
- 这比原来安全很多，但还不是基于 `IntersectionObserver` 的精确按需策略。

影响：

- 大试题篮下仍会有一定的预取冗余。

### 5. 题型管理闭环还没有真正打通

位置：

- `web/src/components/QuestionTypeManager.vue`
- `web/src/components/QuestionCard.vue`
- `web/src/views/ExamComposeView.vue`

问题：

- 题型 store 已存在，但入口、展示和 section 绑定关系还不够完整。

影响：

- 题型配置仍然更像“可配置数据”，还不是组卷主流程中的第一等能力。

### 6. 导出链路已经接通，但导出前后的交互反馈仍然偏弱

位置：

- `web/src/stores/exam.ts`
- `web/src/views/ExamComposeView.vue`
- `web/src/views/ExamPreviewView.vue`

问题：

- 当前只做了按钮 loading 和下载触发。
- 还没有导出历史、失败细分提示、导出参数面板。

影响：

- 复杂试卷导出时，用户只能得到成功/失败的粗粒度反馈。

## 结论

当前 `web` 端最关键的方向已经从“补 OCR/试卷解析”转为三件更实际的事：

1. 把题库分页继续推进到“服务端条件查询 + 前端缓冲展示”。
2. 把导出链路围绕 `exam-service -> export-sidecar` 做完整交互收口。
3. 把桌面 Web 的布局与响应式稳定性真正补齐。
