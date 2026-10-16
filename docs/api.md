# API 说明

浏览器统一访问 `/api`；除登录和健康检查外均需 `Authorization: Bearer <token>`。JSON 错误格式为 `{"message":"错误说明"}`。识别被拒绝是成功处理的业务判断，HTTP 200，`allowed=false`；无鉴权、参数错误等使用相应 4xx / 5xx。

## Spring Boot

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/health` | Java 进程存活；不代表所有依赖可用 |
| POST | `/api/auth/login` | `{username,password}` → `{token,username,expiresIn}` |
| POST | `/api/auth/logout` | 撤销当前 Redis 会话 |
| GET | `/api/dashboard` | 人员、门禁、今日统计、七日趋势、最近记录和 Python 状态 |
| GET | `/api/persons?search=&page=1&size=10` | 人员列表，分页大小 1–100 |
| POST | `/api/persons` | 新增人员 |
| PUT | `/api/persons/{id}` | 修改人员 |
| DELETE | `/api/persons/{id}` | 删除人员及特征、权限，保留日志快照 |
| POST | `/api/persons/{id}/face` | `multipart/form-data`，字段 `file` |
| DELETE | `/api/persons/{id}/face` | 清除人脸特征 |
| GET | `/api/doors` | 门禁列表 |
| POST | `/api/doors` | 新增门禁 |
| PUT | `/api/doors/{id}` | 编辑或停用门禁 |
| GET | `/api/grants` | 授权列表 |
| POST | `/api/grants` | 新增授权；同人员同门禁已有授权时更新 |
| DELETE | `/api/grants/{id}` | 撤销授权 |
| POST | `/api/access/recognize?doorId=1` | `multipart/form-data`，字段 `file` |
| GET | `/api/logs?search=&allowed=true&page=1&size=10` | 按关键词和结果筛选日志，省略 allowed 表示全部 |

人员请求示例：

```json
{"personNo":"S2026001","name":"张同学","department":"计算机学院","enabled":true}
```

门禁请求示例：

```json
{"name":"实验室入口","location":"A301","enabled":true}
```

授权请求示例（请按实际演示时间修改）：

```json
{"personId":1,"doorId":1,"validFrom":"2026-10-01T00:00:00","validUntil":"2026-12-31T23:59:59"}
```

识别响应示例：

```json
{
  "allowed": true,
  "doorOpen": true,
  "reason": "ALLOWED",
  "message": "识别成功，模拟开门 5 秒",
  "personId": 1,
  "personName": "张同学",
  "score": 0.88,
  "logId": 1
}
```

拒绝原因：`UNKNOWN_FACE`、`AMBIGUOUS_FACE`、`PERSON_DISABLED`、`DOOR_DISABLED`、`NO_PERMISSION`、`COOLDOWN`、`INVALID_IMAGE`、`FACE_UNAVAILABLE`。未开始或已过期的权限统一视为 `NO_PERMISSION`。

分页结果格式：`{items:[], total:0, page:1, size:10}`。前端不接收原始人脸特征。

## 内部 Python 接口

仅供 Java 调用，除 `/health` 外必须带 `X-Service-Key`，不直接向网页或外网开放。

| 方法 | 路径 | 请求 / 返回 |
| --- | --- | --- |
| GET | `/health` | 模型已就绪返回 200；缺模型返回 503 |
| POST | `/extract` | 图片 `file` → `{feature:[128维],model,box:[x,y,w,h]}` |
| POST | `/match` | `{feature,gallery:[{id,feature}],threshold,margin}` → `{personId,score,runnerUpScore,ambiguous}` |

`personId=null` 表示没有可信匹配。图片解码失败、无人脸、多人脸和过小人脸返回 422。特征维度、有限数值、候选 ID 唯一性均会检查。
