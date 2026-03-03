# QForge 图片资源存储设计

> 日期：2026-03-04  
> 状态：**已实现**（v2 最终方案）  
> 关联需求：题干配图、选项配图持久化到云端题库

---

## ⭐ 最终实现方案（v2）

**核心约束**：每题最多 10 张图，每张最多 30KB（二进制），**一次 `PUT /stem` 请求原子提交所有图片**。

### 一次请求原则

```
PUT /api/questions/{uuid}/stem
{
  "stemXml": "<stem>...<image ref=\"img-1\"/>...</stem>",
  "mainTags": [...],
  "secondaryTagsText": "...",
  "inlineImages": {
    "img-1": { "imageData": "iVBORw0K...(base64)...", "mimeType": "image/png" },
    "img-2": { "imageData": "iVBORw0K...", "mimeType": "image/png" }
  }
}
```

- **上传**：确认题干时一次性带上全部配图，后端原子写入 `q_question_asset`
- **下载**：`GET /api/questions` 返回时携带 `assets` 字段（含完整 base64），前端 sync 后直接还原图片
- **约束验证**：服务端硬校验 count ≤ 10、size ≤ 30KB，超限返回 422
- **同步策略**：每次 sync 时服务端数据权威覆盖，本地草稿图片在确认题干前保留

> **为什么不用对象存储？**  
> 30KB × 10 = 300KB max per question，base64 膨胀后 ≤ 400KB，MySQL MEDIUMTEXT 完全可承受，无需额外基础设施。题目数量有限（个人题库），总存储量不成问题。一次请求完成所有操作，无需分布式事务。

### 实现文件清单

| 文件 | 变更说明 |
|------|---------|
| `backend/sql/init-schema.sql` | `q_question_asset` 增加 `ref_key` 列 + 索引；`LONGTEXT→MEDIUMTEXT` |
| `dto/InlineImageEntry.java` | 新建，内嵌图片传输 DTO |
| `dto/QuestionAssetResponse.java` | 新建，图片查询响应 DTO |
| `dto/UpdateStemRequest.java` | 增加 `inlineImages` Map 字段 |
| `dto/QuestionOverviewResponse.java` | 增加 `assets` 字段 |
| `entity/QuestionAsset.java` | 增加 `refKey` 字段 |
| `repository/QuestionAssetRepository.java` | 增加批量查询 `findActiveByQuestionIds` |
| `service/QuestionCommandServiceImpl.java` | `updateStem` + `syncInlineImages` + `listUserQuestions` 携带图片 |
| `controller/QuestionController.java` | 增加 `GET /{uuid}/assets` 接口 |
| `frontend/src/renderer.js` | `confirmStem`/`bankSaveStem` 携带图片；`syncQuestions`/`createEntry` 还原图片 |

---

## ~~v1 草稿~~（已废弃）

---

## 一、现状总览

### 1.1 前端图片业务（6 种场景）

| # | 场景 | 触发方式 | 图片格式 | 当前去向 | 是否持久 |
|---|------|---------|---------|---------|---------|
| ① | **OCR 截图 — 题干识别** | Ctrl+Alt+A / 截图按钮 | 裸 base64 | POST `/api/questions/{uuid}/ocr-tasks` → 后端 `q_ocr_task.image_base64` | ✅ 后端 DB (LONGTEXT) |
| ② | **OCR 截图 — 答案识别** | 答案阶段自动切换 | 裸 base64 | 同上 (bizType=ANSWER_CONTENT) | ✅ 后端 DB (LONGTEXT) |
| ③ | **题干配图（inline image）** | Ctrl+Alt+I / "截图插图" 按钮 | 裸 base64 | `entry.inlineImages["img-N"]`，仅存 localStorage | ❌ 纯本地 |
| ④ | **选项配图（choice image）** | Ctrl+Alt+1 / 选项"截图插图" | 裸 base64 | 同 ③，存入 `inlineImages`，XML 中写 `<image ref="img-N"/>` | ❌ 纯本地 |
| ⑤ | **手动上传 — 题干 OCR** | file input 选文件 | File → base64 | 同 ① | ✅ 后端 DB |
| ⑥ | **题干旧版配图（遗留）** | 已停用 | base64 | `entry.stemImageBase64`，仅 localStorage | ❌ 纯本地 |

### 1.2 后端现有能力

| 层次 | 组件 | 现状 |
|------|------|------|
| **数据表** | `q_question_asset` | 已建表，含 `image_data LONGTEXT` (base64)、`asset_type`、`mime_type` 等列 |
| **实体** | `QuestionAsset` | 已定义，映射到 `q_question_asset` |
| **Repository** | `QuestionAssetRepository` | 已有 `findByAssetUuid`、`findByQuestionId`、`findByQuestionIdAndAssetType`、`softDeleteByQuestionId` |
| **Controller** | ❌ 无 | **没有图片上传/下载 API** |
| **Service** | ❌ 无 | **没有图片存储的业务服务** |
| **对象存储** | ❌ 无 | 无 MinIO/S3，无 multipart 配置 |
| **OCR 图片** | `q_ocr_task.image_base64` | OCR 任务表中存有识别用的截图（LONGTEXT），但识别完不会迁移到 asset |

### 1.3 核心问题

```
╔══════════════════════════════════════════════════════════════════════╗
║  前端 stem XML 中 <image ref="img-1"/> 的图片本体                     ║
║  完全只存在 localStorage，从未上传后端。                               ║
║                                                                      ║
║  → 换设备 / 清缓存 / 多人协作 → 图片全丢                              ║
║  → 后端持有破碎的 XML 引用，无法还原完整题目                           ║
╚══════════════════════════════════════════════════════════════════════╝
```

---

## 二、后端设计方案

### 2.1 设计目标

| 优先级 | 目标 |
|--------|------|
| P0 | **高并发写入**：截图连续操作（Ctrl+Alt+1 连按多次）不阻塞 UI |
| P0 | **图片持久化**：所有 inline 图片上传到后端，跨设备可用 |
| P1 | **低延迟读取**：题目预览/编辑时图片秒级加载 |
| P1 | **存储成本可控**：避免 MySQL LONGTEXT 膨胀 |
| P2 | **渐进迁移**：兼容已有 `q_question_asset` 表结构 |

### 2.2 架构总览

```
┌─────────────┐     multipart/form-data      ┌──────────────────────┐
│  Electron    │  ────────────────────────►   │  question-service    │
│  Frontend    │  POST /api/assets/upload     │                      │
│              │                               │  AssetController     │
│              │  ◄────────────────────────   │    ↓                 │
│              │  { assetUuid, url }           │  AssetService        │
│              │                               │    ↓                 │
│              │  GET /api/assets/{uuid}       │  StorageStrategy     │
│              │  ────────────────────────►   │    ├ LocalFileStorage │
│              │                               │    └ MinioStorage     │
│              │  ◄────────────────────────   │         ↓             │
│              │  302 → file / binary stream   │  q_question_asset    │
└─────────────┘                               └──────────────────────┘
                                                       │  元数据
                                                       ▼
                                               ┌──────────┐
                                               │  MySQL    │
                                               │  (metadata│
                                               │   only)   │
                                               └──────────┘
                                                       │  文件本体
                                                       ▼
                                               ┌──────────┐
                                               │  本地磁盘  │
                                               │  /MinIO   │
                                               └──────────┘
```

### 2.3 存储策略：文件存储 + DB 元数据（数据库不存 base64）

**核心思路**：`q_question_asset.image_data` 列不再存 base64 大字段，改为存**文件路径/对象 key**。图片二进制写入**本地磁盘**（开发阶段）或 **MinIO/S3**（生产环境），通过 `StorageStrategy` 接口切换。

```java
public interface StorageStrategy {
    /** 存储图片，返回存储 key (如 "2026/03/04/{assetUuid}.png") */
    String store(String assetUuid, byte[] data, String mimeType);
    
    /** 根据 key 获取图片字节 */
    byte[] load(String storageKey);
    
    /** 删除 */
    void delete(String storageKey);
}
```

**为什么不继续用 MySQL LONGTEXT？**

| 指标 | LONGTEXT base64 | 文件存储 + DB 元数据 |
|------|-----------------|---------------------|
| 单张 500KB 图 (base64 膨胀 ×1.33) | 写入 ~667KB 到 MySQL | 写入 500KB 到磁盘，DB 仅 ~200B 元数据 |
| 100 并发写入 | InnoDB row lock + redo log 压力大 | 文件 I/O 天然分散，DB 仅写小行 |
| 读取（渲染题目） | 全行 SELECT 含大字段，buffer pool 污染 | DB 查元数据 → 文件读取，不经过 MySQL |
| 备份 | mysqldump 极慢 | DB 备份秒级，文件单独归档 |

### 2.4 详细 API 设计

#### API 1：上传图片

```
POST /api/assets/upload
Content-Type: multipart/form-data

Form fields:
  file          : (binary) 图片文件
  questionUuid  : (string) 关联题目 UUID
  assetType     : (string) STEM_IMAGE | CHOICE_IMAGE

Response 200:
{
  "code": 200,
  "data": {
    "assetUuid": "a1b2c3d4-...",
    "questionUuid": "q5e6f7...",
    "assetType": "STEM_IMAGE",
    "url": "/api/assets/a1b2c3d4-...",
    "mimeType": "image/png",
    "size": 51234
  }
}
```

**性能要点**：
- 使用 `multipart/form-data` 而非 JSON base64，减少 33% 传输体积
- Spring Boot 配置 `spring.servlet.multipart.max-file-size=10MB`
- 异步写文件：接收完 multipart 后，先写 DB 元数据行（毫秒级），再 `@Async` 写入文件存储
- 或者直接同步写（磁盘/MinIO 写入通常 <50ms for 1MB），但 DB 事务只包含元数据

#### API 2：批量上传（高并发优化的关键）

```
POST /api/assets/upload-batch
Content-Type: multipart/form-data

Form fields:
  files[]       : (binary[]) 多张图片
  questionUuid  : (string)
  assetType     : (string)

Response 200:
{
  "code": 200,
  "data": [
    { "assetUuid": "...", "url": "...", "originalName": "img-1.png" },
    { "assetUuid": "...", "url": "...", "originalName": "img-2.png" }
  ]
}
```

**性能要点**：
- 前端截图后自动排队上传，批量接口减少 HTTP 连接数
- 服务端使用 `CompletableFuture` 或虚拟线程并发写多个文件
- 单次 DB batch insert 元数据

#### API 3：获取图片

```
GET /api/assets/{assetUuid}
Accept: image/*

Response:
  200 OK
  Content-Type: image/png
  Cache-Control: public, max-age=31536000, immutable
  Body: <binary image data>
```

**性能要点**：
- 图片内容不可变（UUID 天然幂等），设置**永久缓存** `Cache-Control: immutable`
- 浏览器/Electron 只请求一次，后续全走本地缓存
- 可选：使用 `ETag` 头进行条件请求

#### API 4：按题目查询所有图片

```
GET /api/questions/{questionUuid}/assets
Response 200:
{
  "code": 200,
  "data": [
    { "assetUuid": "...", "assetType": "STEM_IMAGE", "url": "/api/assets/...", "mimeType": "image/png" },
    { "assetUuid": "...", "assetType": "CHOICE_IMAGE", "url": "/api/assets/...", "mimeType": "image/png" }
  ]
}
```

**性能要点**：
- 渲染题目时一次性拿到所有图片元数据
- 配合 `<image ref="img-1" />` 中的 ref 与 `assetUuid` 映射
- 不返回二进制，仅返回 URL 列表，浏览器并行加载

#### API 5：删除图片

```
DELETE /api/assets/{assetUuid}

Response 200:
{ "code": 200, "data": null }
```

- 软删除：`UPDATE q_question_asset SET deleted = TRUE`
- 物理文件保留 7 天后异步清理（防误删）

---

### 2.5 数据库改造

#### 修改 `q_question_asset` 表

```sql
-- 新增列
ALTER TABLE q_question_asset
  ADD COLUMN storage_key  VARCHAR(512) NULL COMMENT '文件存储路径 (如 2026/03/04/{uuid}.png)',
  ADD COLUMN file_size    BIGINT       NULL COMMENT '文件大小 (bytes)',
  ADD COLUMN ref_key      VARCHAR(64)  NULL COMMENT '前端 XML 中的引用 key (如 img-1)';

-- image_data 列保持向后兼容，新增图片不再写入该列
-- 后续可通过迁移脚本将已有 base64 数据迁移到文件存储后置 NULL
```

#### 新增索引

```sql
CREATE INDEX idx_asset_question_type ON q_question_asset(question_id, asset_type, deleted);
CREATE INDEX idx_asset_uuid          ON q_question_asset(asset_uuid);
CREATE INDEX idx_asset_ref_question  ON q_question_asset(question_id, ref_key);
```

### 2.6 Java 实现核心类

```
question-service/
  src/main/java/.../question/
    controller/
      AssetController.java          ← REST 入口
    service/
      AssetService.java             ← 业务逻辑接口
      AssetServiceImpl.java         ← 实现
    storage/
      StorageStrategy.java          ← 存储策略接口
      LocalFileStorageStrategy.java ← 本地磁盘实现
      MinioStorageStrategy.java     ← MinIO 实现（可选）
    config/
      StorageConfig.java            ← 存储配置 + multipart 配置
```

#### AssetController（伪代码）

```java
@RestController
@RequestMapping("/api/assets")
public class AssetController {

    @PostMapping("/upload")
    public R<AssetUploadResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("questionUuid") String questionUuid,
            @RequestParam("assetType") String assetType,
            @RequestHeader("X-Auth-User") String user) {
        return R.ok(assetService.upload(file, questionUuid, assetType, user));
    }

    @PostMapping("/upload-batch")
    public R<List<AssetUploadResponse>> uploadBatch(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam("questionUuid") String questionUuid,
            @RequestParam("assetType") String assetType,
            @RequestHeader("X-Auth-User") String user) {
        return R.ok(assetService.uploadBatch(files, questionUuid, assetType, user));
    }

    @GetMapping("/{assetUuid}")
    public ResponseEntity<Resource> download(@PathVariable String assetUuid) {
        // 返回 Resource + Cache-Control: immutable
    }
}
```

#### AssetServiceImpl 高并发关键逻辑

```java
@Service
public class AssetServiceImpl implements AssetService {

    private final StorageStrategy storage;
    private final QuestionAssetRepository assetRepo;
    private final Executor ioExecutor;  // 虚拟线程池 or ForkJoinPool

    @Override
    public List<AssetUploadResponse> uploadBatch(
            List<MultipartFile> files, String questionUuid, String assetType, String user) {
        
        // 1. 并行写入文件存储（I/O 密集，适合虚拟线程）
        List<CompletableFuture<AssetUploadResponse>> futures = files.stream()
            .map(f -> CompletableFuture.supplyAsync(() -> uploadSingle(f, questionUuid, assetType), ioExecutor))
            .toList();

        // 2. 等待全部完成
        return futures.stream()
            .map(CompletableFuture::join)
            .toList();
    }

    private AssetUploadResponse uploadSingle(MultipartFile file, String questionUuid, String assetType) {
        String assetUuid = UUID.randomUUID().toString();
        String mimeType = file.getContentType();
        byte[] data = file.getBytes();
        
        // 写文件存储
        String storageKey = storage.store(assetUuid, data, mimeType);
        
        // 写 DB 元数据（轻量行）
        QuestionAsset asset = new QuestionAsset();
        asset.setAssetUuid(assetUuid);
        asset.setQuestionId(resolveQuestionId(questionUuid));
        asset.setAssetType(assetType);
        asset.setStorageKey(storageKey);
        asset.setMimeType(mimeType);
        asset.setFileSize((long) data.length);
        assetRepo.save(asset);
        
        return new AssetUploadResponse(assetUuid, "/api/assets/" + assetUuid, mimeType, data.length);
    }
}
```

#### LocalFileStorageStrategy

```java
@Component
@ConditionalOnProperty(name = "storage.type", havingValue = "local", matchIfMissing = true)
public class LocalFileStorageStrategy implements StorageStrategy {

    @Value("${storage.local.base-path:./data/assets}")
    private String basePath;

    @Override
    public String store(String assetUuid, byte[] data, String mimeType) {
        // 按日期分目录，避免单目录文件过多
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE); // 20260304
        String ext = mimeTypeToExt(mimeType); // png / jpeg
        String key = date + "/" + assetUuid + "." + ext;
        Path path = Path.of(basePath, key);
        Files.createDirectories(path.getParent());
        Files.write(path, data, StandardOpenOption.CREATE_NEW);
        return key;
    }

    @Override
    public byte[] load(String storageKey) {
        return Files.readAllBytes(Path.of(basePath, storageKey));
    }

    @Override
    public void delete(String storageKey) {
        Files.deleteIfExists(Path.of(basePath, storageKey));
    }
}
```

### 2.7 配置

```yaml
# question-service application.yml 新增
spring:
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 50MB    # 批量上传时总大小

storage:
  type: local                    # local | minio
  local:
    base-path: ${STORAGE_PATH:./data/assets}
  # minio:                       # 生产环境启用
  #   endpoint: http://minio:9000
  #   access-key: ${MINIO_ACCESS_KEY}
  #   secret-key: ${MINIO_SECRET_KEY}
  #   bucket: qforge-assets
```

---

## 三、前后端交互改造方案

### 3.1 上传时机：截图完成 → 立即上传

```
用户截图 (Ctrl+Alt+I)
  ↓
onCaptured → attachInlineImage(entry, base64) → inlineImages["img-1"]
  ↓ (同时)
uploadAssetToServer(entry.questionUuid, base64, "STEM_IMAGE")
  ↓
POST /api/assets/upload  →  { assetUuid, url }
  ↓
entry.inlineImages["img-1"] = { base64, assetUuid, url }   ← 增加元数据
entry.stemDraft → <image ref="img-1" asset-uuid="a1b2c3..."/>  ← XML 增加 uuid 属性
```

### 3.2 渲染时机：优先本地 base64，fallback 服务端 URL

```javascript
function resolveStemImage(entry, ref) {
  const img = entry.inlineImages?.[ref];
  if (!img) return "";
  // 优先使用本地 base64 (最快，无网络请求)
  if (img.base64) return img.base64;
  // fallback: 使用服务端 URL (跨设备场景)
  if (img.url) return img.url;
  // 兼容旧数据 (裸 base64 字符串)
  if (typeof img === "string") return img;
  return "";
}
```

### 3.3 同步时机：syncQuestions 时拉取图片 URL

```javascript
async function syncQuestions() {
  // ... 原有逻辑 ...
  
  // 对每个题目，拉取图片资产列表
  for (const entry of merged.values()) {
    const assets = await api(`/api/questions/${entry.questionUuid}/assets`, "GET");
    // 将 assetUuid → url 映射合并到 inlineImages
    // 如果本地已有 base64，保留 base64 (更快)；否则存 url
  }
}
```

### 3.4 确认题干时：XML 中嵌入 asset-uuid

```xml
<!-- 现在 (只有 ref，图片数据纯本地) -->
<image ref="img-1" />

<!-- 改造后 (ref + asset-uuid，图片已在服务端) -->
<image ref="img-1" asset-uuid="a1b2c3d4-..." />
```

后端解析 stem XML 时可通过 `asset-uuid` 定位到 `q_question_asset` 行。

---

## 四、高并发性能设计要点

### 4.1 写入路径优化

| 环节 | 策略 | 效果 |
|------|------|------|
| **传输格式** | multipart 替代 JSON base64 | 减少 33% 体积 |
| **前端上传** | 截图后异步 fire-and-forget 上传 | 不阻塞用户操作 |
| **前端队列** | 维护上传队列，失败自动重试 3 次 | 弱网容错 |
| **后端写入** | 文件 I/O + DB 元数据分离 | DB 事务极轻 (<1ms) |
| **批量上传** | 一次 HTTP 多文件 + 服务端并行写入 | 减少连接开销 |
| **连接池** | 虚拟线程处理文件 I/O | 不阻塞 Tomcat 线程 |

### 4.2 读取路径优化

| 环节 | 策略 | 效果 |
|------|------|------|
| **客户端缓存** | `Cache-Control: immutable, max-age=1y` | 同一图片只请求一次 |
| **本地优先** | 有 base64 → 直接渲染，不请求后端 | 零延迟 |
| **懒加载** | 题目展开时才加载图片，不在列表页加载 | 减少无效请求 |
| **预加载** | 选中题目后预加载所有关联图片 URL | 编辑时无等待 |
| **ETag** | 基于 assetUuid 的强 ETag | 条件请求 304 |

### 4.3 存储扩展路径

```
阶段 1 (当前)：本地磁盘 ./data/assets/
  ↓ 数据量增长
阶段 2：MinIO 单节点（Docker 部署）
  ↓ 分布式需求
阶段 3：MinIO 集群 / AWS S3 / 阿里云 OSS
```

通过 `StorageStrategy` 接口实现零代码切换。

---

## 五、OCR 图片迁移（可选优化）

当前 `q_ocr_task.image_base64` 存储了 OCR 识别用的完整截图。长期方案：

1. OCR 提交时，将图片写文件存储，DB 仅存 `storageKey`
2. 识别完成后可选保留（用于回溯）或定时清理
3. 不作为 MVP 必须，但 `q_ocr_task` 表已有 TB 级膨胀风险

---

## 六、实施计划

| 阶段 | 范围 | 工作量 |
|------|------|--------|
| **Phase 1** | 后端：`AssetController` + `AssetService` + `LocalFileStorageStrategy` + DB migration | 2-3 天 |
| **Phase 2** | 前端：截图后自动上传 + XML 增加 asset-uuid + 渲染 fallback | 1-2 天 |
| **Phase 3** | 前端：syncQuestions 拉取图片 URL + 队列重试 | 1 天 |
| **Phase 4** | 旧数据迁移：localStorage 中已有图片批量上传到后端 | 0.5 天 |
| **Phase 5** | (可选) MinIO 适配 + OCR 图片迁移 | 1 天 |

---

## 七、附录：现有图片数据流全景图

```
                     ┌──────────────────────────────────────────────────────┐
                     │               Electron Frontend                      │
                     │                                                      │
  User Screenshot    │  ┌──────────┐    ┌──────────────┐                   │
  ──────────────►    │  │screenshot │───►│  renderer.js │                   │
                     │  │  .js      │    │              │                   │
                     │  └──────────┘    │  intent?     │                   │
                     │                   │  ┌───────┐   │                   │
                     │                   │  │OCR    │───┼──► POST /ocr-tasks│──► 后端 OCR
                     │                   │  │       │   │    {imageBase64}  │    (q_ocr_task)
                     │                   │  ├───────┤   │                   │
                     │                   │  │INSERT │   │    ┌────────────┐ │
                     │                   │  │IMAGE  │───┼──►│inlineImages││ │
                     │                   │  │       │   │   │ localStorage││ │  ❌ 不上传后端
                     │                   │  └───────┘   │   └────────────┘ │
                     │                   │              │                   │
                     │                   │ confirmStem  │                   │
                     │                   │  PUT /stem   │──► 后端 MySQL
                     │                   │  {stemXml}   │    (只有 XML 文本,
                     │                   │              │     无图片二进制)
                     │                   └──────────────┘                   │
                     └──────────────────────────────────────────────────────┘

                     ┌──────────────────────────────────────────────────────┐
                     │               改造后                                 │
                     │                                                      │
                     │  截图 → inlineImages (本地即时显示)                    │
                     │       → POST /api/assets/upload (异步持久化)          │
                     │       → 拿到 assetUuid                               │
                     │       → XML: <image ref="img-1" asset-uuid="..."/>   │
                     │                                                      │
                     │  渲染 → 优先 base64 (本地)                            │
                     │       → fallback GET /api/assets/{uuid} (跨设备)     │
                     └──────────────────────────────────────────────────────┘
```
