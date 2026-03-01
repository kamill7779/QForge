# QForge Frontend (Electron Demo)

这个前端 Demo 已按任务化流程实现:

1. 登录（支持记住密码）
2. 批量创建录题任务
3. 打开一个任务，上传图片发起题干 OCR
4. 等待异步结果后填充并确认题干
5. 题干确认与答案录入分离处理，各自提供 LaTeX 渲染预览
6. 任务自动进入“待录答案”列表
7. 录入答案（答案 OCR 按钮保留，当前禁用）
8. 完成题目入库（READY）

## 启动

```bash
cd frontend
npm install
npm start
```

默认网关和 WS:

- API: `http://localhost:8080`
- WS: `ws://localhost:8089`

可通过环境变量覆盖:

- `QFORGE_API_BASE_URL`
- `QFORGE_WS_BASE_URL`

## 默认账号

- 用户名: `admin`
- 密码: `admin123`
