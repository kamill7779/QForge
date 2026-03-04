# Question Service 鈥?API 鎺ュ彛鏂囨。

> **鐗堟湰**: 2026-03-02 路 **Base URL**: `/api` 路 **璁よ瘉**: 鎵€鏈夋帴鍙ｉ€氳繃 `X-Auth-User` 璇锋眰澶翠紶閫掑綋鍓嶇敤鎴锋爣璇?

---

## 鐩綍

1. [閫氱敤绾﹀畾](#閫氱敤绾﹀畾)
2. [棰樼洰绠＄悊鎺ュ彛](#棰樼洰绠＄悊鎺ュ彛)
   - [鑾峰彇棰樼洰鍒楄〃](#get-apiquestions)
   - [鍒涘缓鑽夌](#post-apiquestions)
   - [鏇存柊棰樺共](#put-apiquestionsquestionuuidstem)
   - [娣诲姞绛旀](#post-apiquestionsquestionuuidanswers)
   - [缂栬緫绛旀](#put-apiquestionsquestionuuidanswersansweruuid)
   - [鍒犻櫎绛旀](#delete-apiquestionsquestionuuidanswersansweruuid)
   - [瀹屾垚棰樼洰](#post-apiquestionsquestionuuidcomplete)
   - [鍒犻櫎鑽夌](#delete-apiquestionsquestionuuid)
3. [OCR 浠诲姟鎺ュ彛](#ocr-浠诲姟鎺ュ彛)
   - [鎻愪氦 OCR 浠诲姟](#post-apiquestionsquestionuuidocr-tasks)
4. [鏍囩鎺ュ彛](#鏍囩鎺ュ彛)
   - [鑾峰彇鏍囩鐩綍](#get-apitags)
5. [WebSocket 鎺ㄩ€乚(#websocket-鎺ㄩ€?
6. [鏁版嵁缁撴瀯鍙傝€僝(#鏁版嵁缁撴瀯鍙傝€?
7. [閿欒鍝嶅簲鏍煎紡](#閿欒鍝嶅簲鏍煎紡)
8. [涓氬姟閿欒鐮佷竴瑙圿(#涓氬姟閿欒鐮佷竴瑙?

---

## 閫氱敤绾﹀畾

| 椤圭洰 | 璇存槑 |
|------|------|
| **璇锋眰澶?* | `X-Auth-User: <string>` 鈥?褰撳墠鎿嶄綔鐢ㄦ埛锛堢敱 gateway 娉ㄥ叆锛屽墠绔棤闇€鎵嬪姩璁剧疆锛涚己鐪佷负 `anonymous`锛?|
| **Content-Type** | `application/json` |
| **UUID** | 棰樼洰浣跨敤 `questionUuid`锛岀瓟妗堜娇鐢?`answerUuid`锛孫CR 浠诲姟浣跨敤 `taskUuid`锛屽潎涓?36 浣?UUID 瀛楃涓?|
| **棰樼洰鐘舵€?* | `DRAFT`锛堣崏绋匡級鈫?`READY`锛堝畬鎴愶級 |
| **閫昏緫鍒犻櫎** | 绛旀鐨?`DELETE` 鎿嶄綔涓鸿蒋鍒犻櫎锛坄deleted=1`锛夛紝涓嶅奖鍝嶅凡鏈夋暟鎹粺璁?|

---

## 棰樼洰绠＄悊鎺ュ彛

### `GET /api/questions`

鑾峰彇褰撳墠鐢ㄦ埛鐨勬墍鏈夐鐩垪琛紙鍚瓟妗堟憳瑕併€佹爣绛惧揩鐓э級銆?

**Response** `200 OK`

```json
[
  {
    "questionUuid": "550e8400-e29b-41d4-a716-446655440000",
    "status": "DRAFT",
    "stemText": "<stem><p>姹傝В鏂圭▼ <equation>x^2 + 1 = 0</equation></p></stem>",
    "mainTags": [
      {
        "categoryCode": "SUBJECT",
        "categoryName": "瀛︾",
        "tagCode": "MATH",
        "tagName": "鏁板"
      }
    ],
    "secondaryTags": ["楂樿€?, "閫夋嫨棰?],
    "answerCount": 2,
    "answers": [
      {
        "answerUuid": "660e8400-e29b-41d4-a716-446655440001",
        "answerType": "LATEX_TEXT",
        "latexText": "x = \\pm i",
        "sortOrder": 1,
        "official": false
      }
    ],
    "updatedAt": "2026-03-02T14:30:00"
  }
]
```

---

### `POST /api/questions`

鍒涘缓涓€涓┖鐧借崏绋块鐩€傝姹備綋鍙€夆€斺€斿彲浼犲叆鍒濆棰樺共鏂囨湰锛屼篃鍙暀绌哄悗缁€氳繃 `PUT /stem` 璁剧疆銆?

**Request Body**锛堝彲閫夛紝`Content-Type: application/json`锛?

```json
{
  "stemText": "鍙€夌殑鍒濆棰樺共鏂囨湰"
}
```

| 瀛楁 | 绫诲瀷 | 蹇呭～ | 璇存槑 |
|------|------|------|------|
| `stemText` | `string` | 鍚?| 鍒濆棰樺共鏂囨湰锛屽彲绋嶅悗閫氳繃 updateStem 璁剧疆 |

**Response** `201 Created`

```json
{
  "questionUuid": "550e8400-e29b-41d4-a716-446655440000",
  "status": "DRAFT"
}
```

> `Location` 鍝嶅簲澶村寘鍚柊璧勬簮 URI锛歚/api/questions/550e8400-...`

---

### `PUT /api/questions/{questionUuid}/stem`

璁剧疆鎴栨洿鏂伴骞?XML 鏂囨湰銆傛湇鍔＄寮哄埗鎵ц XML Schema 鏍￠獙锛屼笉鍚堣鍒欒繑鍥?`422`銆傚彲鍙嶅璋冪敤浠ヨ鐩栭骞插唴瀹广€?

**Path Parameters**

| 鍙傛暟 | 绫诲瀷 | 璇存槑 |
|------|------|------|
| `questionUuid` | `string` | 棰樼洰 UUID |

**Request Body**

```json
{
  "stemXml": "<stem><p>杩欐槸涓€閬撴暟瀛﹂</p></stem>"
}
```

| 瀛楁 | 绫诲瀷 | 蹇呭～ | 璇存槑 |
|------|------|------|------|
| `stemXml` | `string` | **鏄?* | 绗﹀悎 XML Schema 鐨勯骞叉枃鏈?|

**XML Schema 鏀寔鐨勬爣绛?*: `<stem>`, `<p>`, `<equation>`, `<figure>`, `<table>`, `<blank>`, `<choice-group>`

**Response** `200 OK`

```json
{
  "questionUuid": "550e8400-e29b-41d4-a716-446655440000",
  "status": "DRAFT"
}
```

**閿欒鍦烘櫙**

| 鐘舵€佺爜 | code | 鍘熷洜 |
|--------|------|------|
| `400` | `REQUEST_VALIDATION_FAILED` | `stemXml` 涓虹┖ |
| `404` | `QUESTION_NOT_FOUND` | 棰樼洰涓嶅瓨鍦ㄦ垨涓嶅睘浜庡綋鍓嶇敤鎴?|
| `422` | `STEM_XML_INVALID` | XML 鏍￠獙澶辫触锛堣瑙?`details` 瀛楁锛?|

---

### `POST /api/questions/{questionUuid}/answers`

涓烘寚瀹氶鐩坊鍔犱竴鏉＄瓟妗堛€?

**Path Parameters**

| 鍙傛暟 | 绫诲瀷 | 璇存槑 |
|------|------|------|
| `questionUuid` | `string` | 棰樼洰 UUID |

**Request Body**

```json
{
  "latexText": "x = \\frac{-b \\pm \\sqrt{b^2-4ac}}{2a}"
}
```

| 瀛楁 | 绫诲瀷 | 蹇呭～ | 璇存槑 |
|------|------|------|------|
| `latexText` | `string` | **鏄?* | LaTeX 鏍煎紡绛旀鏂囨湰 |

**Response** `201 Created`

```json
{
  "questionUuid": "550e8400-e29b-41d4-a716-446655440000",
  "status": "DRAFT"
}
```

---

### `PUT /api/questions/{questionUuid}/answers/{answerUuid}`

缂栬緫鎸囧畾绛旀鐨勬枃鏈唴瀹广€?

**Path Parameters**

| 鍙傛暟 | 绫诲瀷 | 璇存槑 |
|------|------|------|
| `questionUuid` | `string` | 棰樼洰 UUID |
| `answerUuid` | `string` | 绛旀 UUID |

**Request Body**

```json
{
  "latexText": "x = 2"
}
```

| 瀛楁 | 绫诲瀷 | 蹇呭～ | 璇存槑 |
|------|------|------|------|
| `latexText` | `string` | **鏄?* | 鏇存柊鍚庣殑 LaTeX 绛旀鏂囨湰 |

**Response** `200 OK`

```json
{
  "questionUuid": "550e8400-e29b-41d4-a716-446655440000",
  "status": "DRAFT"
}
```

**閿欒鍦烘櫙**

| 鐘舵€佺爜 | code | 鍘熷洜 |
|--------|------|------|
| `404` | `QUESTION_NOT_FOUND` | 棰樼洰涓嶅瓨鍦ㄦ垨涓嶅睘浜庡綋鍓嶇敤鎴?|
| `404` | `ANSWER_NOT_FOUND` | 绛旀涓嶅瓨鍦ㄦ垨涓嶅睘浜庤棰樼洰 |

---

### `DELETE /api/questions/{questionUuid}/answers/{answerUuid}`

鍒犻櫎鎸囧畾绛旀銆?*鑷冲皯淇濈暀涓€鏉＄瓟妗?*鈥斺€斿綋棰樼洰浠呭墿鏈€鍚庝竴鏉＄瓟妗堟椂锛屽垹闄よ姹傚皢琚嫆缁濄€?

**Path Parameters**

| 鍙傛暟 | 绫诲瀷 | 璇存槑 |
|------|------|------|
| `questionUuid` | `string` | 棰樼洰 UUID |
| `answerUuid` | `string` | 绛旀 UUID |

**Response** `204 No Content`

**閿欒鍦烘櫙**

| 鐘舵€佺爜 | code | 鍘熷洜 |
|--------|------|------|
| `404` | `QUESTION_NOT_FOUND` | 棰樼洰涓嶅瓨鍦ㄦ垨涓嶅睘浜庡綋鍓嶇敤鎴?|
| `404` | `ANSWER_NOT_FOUND` | 绛旀涓嶅瓨鍦ㄦ垨涓嶅睘浜庤棰樼洰 |
| `422` | `ANSWER_DELETE_LAST_NOT_ALLOWED` | 涓嶈兘鍒犻櫎鏈€鍚庝竴鏉＄瓟妗?|

---

### `POST /api/questions/{questionUuid}/complete`

灏嗛鐩姸鎬佷粠 `DRAFT` 鏍囪涓?`READY`锛堝畬鎴愶級銆傚墠缃潯浠讹細棰樺共涓嶄负绌?**涓?* 鑷冲皯鏈変竴鏉＄瓟妗堛€?

**Response** `200 OK`

```json
{
  "questionUuid": "550e8400-e29b-41d4-a716-446655440000",
  "status": "READY"
}
```

**閿欒鍦烘櫙**

| 鐘舵€佺爜 | code | 鍘熷洜 |
|--------|------|------|
| `404` | `QUESTION_NOT_FOUND` | 棰樼洰涓嶅瓨鍦?|
| `422` | `QUESTION_COMPLETE_VALIDATION_FAILED` | 缂哄皯 stemText 鎴?answers锛坄details.missingFields` 鍒楀嚭缂哄け椤癸級 |

---

### `DELETE /api/questions/{questionUuid}`

鍒犻櫎鑽夌棰樼洰銆?*浠呴檺 `DRAFT` 鐘舵€佷笖鏃犵瓟妗?*鐨勯鐩€?

**Response** `204 No Content`

**閿欒鍦烘櫙**

| 鐘舵€佺爜 | code | 鍘熷洜 |
|--------|------|------|
| `404` | `QUESTION_NOT_FOUND` | 棰樼洰涓嶅瓨鍦?|
| `422` | `QUESTION_DELETE_NOT_ALLOWED` | 闈?DRAFT 鐘舵€佹垨宸叉湁绛旀 |

---

## OCR 浠诲姟鎺ュ彛

### `POST /api/questions/{questionUuid}/ocr-tasks`

鎻愪氦涓€涓?OCR 璇嗗埆浠诲姟銆備换鍔″紓姝ユ墽琛岋紝缁撴灉閫氳繃 **WebSocket** 鎺ㄩ€佺粰瀹㈡埛绔€?

**Path Parameters**

| 鍙傛暟 | 绫诲瀷 | 璇存槑 |
|------|------|------|
| `questionUuid` | `string` | 棰樼洰 UUID |

**Request Body**

```json
{
  "bizType": "QUESTION_STEM",
  "imageBase64": "BASE64_ENCODED_IMAGE_DATA..."
}
```

| 瀛楁 | 绫诲瀷 | 蹇呭～ | 鏍￠獙 | 璇存槑 |
|------|------|------|------|------|
| `bizType` | `string` | **鏄?* | 蹇呴』涓?`QUESTION_STEM` 鎴?`ANSWER_CONTENT` | OCR 涓氬姟绫诲瀷 |
| `imageBase64` | `string` | **鏄?* | `@NotBlank` | Base64 缂栫爜鐨勫浘鐗囨暟鎹?|

**Response** `202 Accepted`

```json
{
  "taskUuid": "770e8400-e29b-41d4-a716-446655440002",
  "status": "PENDING"
}
```

> 浠诲姟鎻愪氦鍚庝负 `PENDING` 鐘舵€併€侽CR 鏈嶅姟瀹屾垚鍚庯紝question-service 鍐呴儴鑷姩灏嗕换鍔℃爣璁颁负 `CONFIRMED`锛堟垚鍔燂級鎴?`FAILED`锛堝け璐ワ級锛屽苟閫氳繃 WebSocket 鎺ㄩ€佺粨鏋溿€?
**鍙兘閿欒**

| 鐘舵€佺爜 | code | 鍘熷洜 |
|--------|------|------|
| `404` | `QUESTION_NOT_FOUND` | 棰樼洰涓嶅瓨鍦ㄦ垨涓嶅睘浜庡綋鍓嶇敤鎴?|
| `409` | `OCR_TASK_CONFLICT` | 鍚屼竴棰樼洰 `ANSWER_CONTENT` 宸插瓨鍦ㄨ繘琛屼腑鐨?OCR 浠诲姟锛坄PENDING/PROCESSING`锛?|

---

## 鏍囩鎺ュ彛

### `GET /api/tags`

鑾峰彇绯荤粺鏍囩鐩綍锛屽寘鍚墍鏈変富鍒嗙被鍙婂叾鍙€夋爣绛撅紝浠ュ強鑷畾涔変簩绾ф爣绛惧垎绫讳俊鎭€?

**Response** `200 OK`

```json
{
  "mainCategories": [
    {
      "categoryCode": "SUBJECT",
      "categoryName": "瀛︾",
      "options": [
        { "tagCode": "MATH", "tagName": "鏁板" },
        { "tagCode": "PHYSICS", "tagName": "鐗╃悊" },
        { "tagCode": "UNCATEGORIZED", "tagName": "鏈垎绫? }
      ]
    },
    {
      "categoryCode": "DIFFICULTY",
      "categoryName": "闅惧害",
      "options": [
        { "tagCode": "EASY", "tagName": "绠€鍗? },
        { "tagCode": "MEDIUM", "tagName": "涓瓑" },
        { "tagCode": "HARD", "tagName": "鍥伴毦" }
      ]
    }
  ],
  "secondaryCategoryCode": "SECONDARY_CUSTOM",
  "secondaryCategoryName": "鑷畾涔夋爣绛?
}
```

---

## WebSocket 鎺ㄩ€?

**杩炴帴绔偣**: WebSocket 杩炴帴鐢?gateway 灞備唬鐞嗭紝question-service 鍐呴儴閫氳繃 `QuestionWsHandler` 绠＄悊浼氳瘽銆?

**娑堟伅鏍煎紡**锛圝SON锛屾湇鍔＄ 鈫?瀹㈡埛绔級锛?

```json
{
  "event": "<浜嬩欢鍚?",
  "payload": { ... }
}
```

### OCR 鎴愬姛浜嬩欢

```json
{
  "event": "ocr.task.succeeded",
  "payload": {
    "taskUuid": "770e8400-e29b-41d4-a716-446655440002",
    "bizType": "QUESTION_STEM",
    "bizId": "550e8400-e29b-41d4-a716-446655440000",
    "recognizedText": "<stem><p>璇嗗埆鍑虹殑棰樺共鍐呭</p></stem>"
  }
}
```

> 褰?`bizType=ANSWER_CONTENT` 鏃讹紝`recognizedText` 蹇呴』涓?`<answer version="1">...</answer>` XML锛? 
> 鑻ュ寘鍚?OCR 瑁佸浘锛屽浘鐗囧紩鐢?key 浣跨敤 `a{task8}-img-N`锛堜緥濡?`a92f6c03-img-1`锛夛紝鍙€氳繃 `GET /api/questions/{questionUuid}/assets` 鑾峰彇瀵瑰簲 base64銆?
> 服务端对此约束做硬保证：若 AI 返回非 XML 或仍包含 `![](page,bbox)` 占位，会兜底转为合规 answer XML 后再推送。
### OCR 澶辫触浜嬩欢

```json
{
  "event": "ocr.task.failed",
  "payload": {
    "taskUuid": "770e8400-e29b-41d4-a716-446655440002",
    "bizType": "QUESTION_STEM",
    "bizId": "550e8400-e29b-41d4-a716-446655440000",
    "errorMessage": "Image too blurry to recognize"
  }
}
```

> 鍓嶇鏀跺埌 `ocr.task.succeeded` 鍚庯紝鍙皢 `recognizedText` 灞曠ず缁欑敤鎴风‘璁?淇敼锛岀劧鍚庤皟鐢?`PUT /api/questions/{questionUuid}/stem`锛堥骞诧級鎴?`POST .../answers`锛堢瓟妗堬級淇濆瓨缁撴灉銆傜瓟妗堜繚瀛樺悗閰嶅浘钀藉埌 `q_answer_asset`銆?
---

## 鏁版嵁缁撴瀯鍙傝€?

### Request DTOs

#### CreateQuestionRequest

```typescript
{
  stemText?: string    // 鍙€夛紝鍒濆棰樺共鏂囨湰
}
```

#### UpdateStemRequest

```typescript
{
  stemXml: string      // 蹇呭～锛孹ML 鏍煎紡棰樺共
}
```

#### CreateAnswerRequest

```typescript
{
  latexText: string    // 蹇呭～锛孡aTeX 绛旀鏂囨湰
}
```

#### UpdateAnswerRequest

```typescript
{
  latexText: string    // 蹇呭～锛屾洿鏂扮殑 LaTeX 绛旀鏂囨湰
}
```

#### OcrTaskSubmitRequest

```typescript
{
  bizType: "QUESTION_STEM" | "ANSWER_CONTENT"   // 蹇呭～
  imageBase64: string                            // 蹇呭～锛孊ase64 鍥剧墖
}
```

### Response DTOs

#### QuestionStatusResponse

```typescript
{
  questionUuid: string
  status: "DRAFT" | "READY"
}
```

#### QuestionOverviewResponse

```typescript
{
  questionUuid: string
  status: "DRAFT" | "READY"
  stemText: string | null
  mainTags: QuestionMainTagResponse[]
  secondaryTags: string[]
  answerCount: number
  answers: AnswerOverviewResponse[]
  updatedAt: string                    // ISO 8601, e.g. "2026-03-02T14:30:00"
}
```

#### AnswerOverviewResponse

```typescript
{
  answerUuid: string
  answerType: "LATEX_TEXT"
  latexText: string
  sortOrder: number
  official: boolean
}
```

#### QuestionMainTagResponse

```typescript
{
  categoryCode: string
  categoryName: string
  tagCode: string
  tagName: string
}
```

#### OcrTaskAcceptedResponse

```typescript
{
  taskUuid: string
  status: "PENDING"
}
```

#### TagCatalogResponse

```typescript
{
  mainCategories: MainTagCategoryResponse[]
  secondaryCategoryCode: string
  secondaryCategoryName: string
}
```

#### MainTagCategoryResponse

```typescript
{
  categoryCode: string
  categoryName: string
  options: TagOptionResponse[]
}
```

#### TagOptionResponse

```typescript
{
  tagCode: string
  tagName: string
}
```

---

## 閿欒鍝嶅簲鏍煎紡

鎵€鏈変笟鍔￠敊璇拰鍙傛暟鏍￠獙閿欒杩斿洖缁熶竴 JSON 缁撴瀯锛?

```json
{
  "code": "ERROR_CODE",
  "message": "Human-readable error message",
  "traceId": "a1b2c3d4e5f6...",
  "details": { }
}
```

| 瀛楁 | 绫诲瀷 | 璇存槑 |
|------|------|------|
| `code` | `string` | 鏈哄櫒鍙鐨勯敊璇爜 |
| `message` | `string` | 閿欒鎻忚堪 |
| `traceId` | `string` | 32 浣嶈拷韪?ID锛岀敤浜庢棩蹇楁帓鏌?|
| `details` | `object` | 琛ュ厖淇℃伅锛岀粨鏋勫洜閿欒鑰屽紓 |

---

## 涓氬姟閿欒鐮佷竴瑙?

| code | HTTP 鐘舵€佺爜 | 瑙﹀彂鍦烘櫙 | details 绀轰緥 |
|------|-------------|----------|-------------|
| `REQUEST_VALIDATION_FAILED` | `400` | 璇锋眰浣撴牎楠屽け璐ワ紙濡?`@NotBlank` 鏈弧瓒筹級 | `{ "errors": "..." }` |
| `QUESTION_NOT_FOUND` | `404` | 棰樼洰涓嶅瓨鍦ㄦ垨涓嶅睘浜庡綋鍓嶇敤鎴?| `{ "questionUuid": "...", "requestUser": "..." }` |
| `ANSWER_NOT_FOUND` | `404` | 绛旀涓嶅瓨鍦ㄦ垨涓嶅睘浜庤棰樼洰 | `{ "questionUuid": "...", "answerUuid": "..." }` |
| `STEM_XML_INVALID` | `422` | 棰樺共 XML 涓嶇鍚?Schema 瑙勮寖 | 鍙栧喅浜庢牎楠屽櫒杈撳嚭 |
| `OCR_TASK_CONFLICT` | `409` | 鍚岄 `ANSWER_CONTENT` 宸插瓨鍦ㄨ繘琛屼腑浠诲姟锛屾嫆缁濋噸澶嶆彁浜?| `{ "questionUuid": "...", "bizType": "ANSWER_CONTENT" }` |
| `QUESTION_COMPLETE_VALIDATION_FAILED` | `422` | 瀹屾垚棰樼洰鏃剁己灏戝繀濉」 | `{ "missingFields": ["stemText", "answers"] }` |
| `QUESTION_DELETE_NOT_ALLOWED` | `422` | 鍒犻櫎鏉′欢涓嶆弧瓒?| `{ "questionUuid": "...", "status": "READY", "answerCount": 2 }` |
| `ANSWER_DELETE_LAST_NOT_ALLOWED` | `422` | 灏濊瘯鍒犻櫎鏈€鍚庝竴鏉＄瓟妗?| `{ "questionUuid": "...", "answerUuid": "...", "answerCount": 1 }` |
