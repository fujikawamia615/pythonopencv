# FaceGate · 人脸识别门禁系统

基于 **Vue 3 + Element Plus、Spring Boot、Python / OpenCV、MySQL 和 Redis** 的毕业设计演示项目。

通过上传图片完成「人员录入 → 人脸特征提取 → 身份识别 → 门禁权限校验 → 模拟开门 → 通行记录」闭环。第一版不依赖摄像头或门锁硬件，使用真实的人脸检测与特征比对，不使用随机识别结果。

## 功能

- 管理员登录、退出；BCrypt 密码哈希与 Redis 有效期会话。
- 人员新增、编辑、搜索、分页、禁用和删除。
- 上传单人照片录入、更新、清除人脸特征；拒绝重复人员的人脸录入。
- 多门禁管理、启用 / 停用。
- 按「人员 + 门禁 + 起止时间」配置或撤销权限。
- 图片识别，检查陌生人、匹配歧义、人员状态、门禁状态、有效授权和重复通行。
- 成功时页面模拟开门 5 秒；成功与失败均记录通行判断。
- 通行记录搜索、结果筛选和分页；首页统计与近 7 日趋势。
- JPEG / PNG 图片校验、5 MB 限制、单人脸约束、内部服务密钥。

## 项目结构

```text
pythonopencv/
├── frontend/                    # Vue 3 + Element Plus + Vite
│   └── src/views/               # 登录、总览、人员、门禁、授权、识别、日志
├── backend/                     # Java 17 + Spring Boot
│   └── src/main/resources/
│       ├── application.yml      # 环境配置
│       └── schema.sql           # MySQL 建表脚本，启动时自动执行
├── face-service/                # Python 3.12 + FastAPI + OpenCV
│   ├── app/                     # 图片检测、特征提取和比对接口
│   ├── tests/                   # 算法规则与接口测试
│   └── download_models.py       # 模型下载与 SHA-256 校验
├── docs/                        # 架构、接口与演示说明
├── .env.example
└── compose.yaml                 # 五个服务的本地容器编排
```

## 快速启动：推荐 Docker Compose

准备 Docker Desktop（或 Docker Engine + Compose v2）、Git 和 Python 3.12。Python 在此步骤只运行标准库模型下载脚本，无需提前安装 OpenCV。首次构建需联网下载依赖和镜像。

```bash
git clone https://github.com/fujikawamia615/pythonopencv.git
cd pythonopencv
cp .env.example .env
```

Windows PowerShell 将复制命令换为：

```powershell
Copy-Item .env.example .env
```

编辑 `.env`：设置管理员密码、MySQL 密码和内部识别服务密钥；管理员密码至少 12 个字符且不超过 72 个 UTF-8 字节，服务密钥至少 16 个字符。示例值仅方便理解配置，建议更换。

```bash
python face-service/download_models.py
docker compose up -d --build
docker compose ps
```

浏览器打开 **http://localhost:8080**，使用 `.env` 中的 `ADMIN_USERNAME` 和 `ADMIN_PASSWORD` 登录。管理员默认用户名为 `admin`。首次启动会创建数据库表、管理员和一个「实验室入口」门禁，人员与通行记录初始为空。

模型下载失败时，可以从 [YuNet 模型目录](https://github.com/opencv/opencv_zoo/tree/main/models/face_detection_yunet) 和 [SFace 模型目录](https://github.com/opencv/opencv_zoo/tree/main/models/face_recognition_sface) 下载对应的完整 ONNX 文件放进 `face-service/models/`，然后重新运行脚本校验。不要把 Git LFS 指针文本当成模型。

常用命令：

```bash
docker compose logs -f backend face-service
docker compose stop
docker compose down
```

`docker compose down` 会保留 MySQL / Redis 数据卷；不要随意加 `-v`，那会删除数据库数据。管理员仅在数据库为空时初始化；之后修改 `.env` 的管理员密码不会覆盖已有密码。

## 本机开发

需要 Java 17、Maven 3.9、Node.js 22.12+、Python 3.12、MySQL 8.4 和 Redis 7.x。可以使用 IDE 分别运行三个服务。

1. MySQL 中创建数据库和用户，执行一次：

```sql
CREATE DATABASE face_gate CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'face_gate'@'localhost' IDENTIFIED BY 'your-db-password';
GRANT ALL PRIVILEGES ON face_gate.* TO 'face_gate'@'localhost';
```

2. 启动 Redis，在 IDE / 终端设置后端环境变量（`.env` 由 Compose 使用，直接运行 Java / Python 时不会自动加载它）：

| 变量 | 本机开发值 |
| --- | --- |
| `DB_PASSWORD` | 上一步设置的数据库密码 |
| `ADMIN_USERNAME` | `admin` |
| `ADMIN_PASSWORD` | 自定义管理员密码 |
| `FACE_SERVICE_KEY` | 与 Python 端相同的内部密钥 |
| `FACE_SERVICE_URL` | `http://localhost:8000`（默认） |
| `REDIS_HOST` | `localhost`（默认） |

例如 PowerShell（Java 与 Python 的终端都需要设置 `FACE_SERVICE_KEY`）：

```powershell
$env:DB_PASSWORD="your-db-password"
$env:ADMIN_PASSWORD="YourAdminPassword_2026!"
$env:FACE_SERVICE_KEY="YourInternalServiceKey_2026!"
```

3. Python 识别服务，单独终端运行：

```bash
cd face-service
python -m venv .venv
# Windows: .venv\Scripts\Activate.ps1
# macOS / Linux: source .venv/bin/activate
python -m pip install -r requirements.txt
python download_models.py
python -m uvicorn app.main:app --host 127.0.0.1 --port 8000 --workers 1
```

4. Spring Boot 后端，单独终端运行：

```bash
cd backend
mvn spring-boot:run
```

5. Vue 前端，单独终端运行：

```bash
cd frontend
npm ci
npm run dev
```

开发页面通常为 **http://localhost:5173**，Vite 将 `/api` 转发至 Spring Boot 的 8080 端口。开发服务器和容器前端都使用同源代理，因此不需要开放任意来源的 CORS。

## 五分钟演示

1. 登录，首页确认 Python 服务显示「模型已就绪」。
2. 在人员管理创建 `S2026001 / 张同学 / 计算机学院`。
3. 点击人脸录入，上传经本人许可的清晰单人正面照，保存特征。
4. 在通行授权中选择该人员、实验室入口，设置包含当前时间的有效期。
5. 在识别演示选择实验室入口，上传同一人的另一张清晰照片，查看「允许通行」与模拟开门。
6. 五秒内重复提交，可看到「重复通行」；撤销权限后重试可看到「无有效权限」。
7. 上传未录入人员、空白图或多人照，查看拒绝结果及通行日志。
8. 返回首页检查统计和趋势。

**照片质量会影响比对结果。** 请尽量使用正面、光照均匀、人脸未遮挡的图片。系统默认余弦阈值为 `0.45`，第一名和第二名差值小于 `0.05` 时拒绝歧义匹配。阈值通过 `.env` 配置，修改后重建 / 重启后端生效。阈值是演示起点，需要用自己的样本验证，不能把相似度数值当作识别准确率。

## 验证

```bash
cd frontend
npm ci
npm run build

cd ../backend
mvn test

cd ../face-service
python -m pip install -r requirements-dev.txt
python -m pytest -q
```

Java 测试用真实 Spring MVC、H2 的 MySQL 兼容模式和业务层，隔离 Redis / Python；Python 测试验证特征搜索、单人脸规则、图像校验与服务鉴权。测试不要求提交任何个人照片或模型权重，真实图像推理则需先下载模型。

## 设计说明与边界

- [架构与数据库](docs/architecture.md)
- [接口说明](docs/api.md)
- [验收与排错](docs/verification.md)
- 原始上传照片只在请求内用于计算，不保存到磁盘或 Git；MySQL 保存人脸特征和业务记录。人脸特征仍属于敏感数据，应按演示参与者的授权管理。
- 本版为管理员操作的图片演示，只有管理员角色；无摄像头、活体检测、真实门锁协议或访客端。图片输入无法证明真人在场。
- 当前按单后端实例、小规模人员库设计，使用全量特征进行 1:N 比对；不宣称生产门禁可用性或万人级识别性能。
- Docker 默认仅将网页端口绑定到本机，MySQL、Redis、Python 均不直接暴露宿主机端口。

## 技术参考

- [OpenCV 人脸检测与识别教程](https://docs.opencv.org/4.x/d0/dd4/tutorial_dnn_face.html)
- [Vue 官方指南](https://vuejs.org/guide/)
- [Element Plus 官方文档](https://element-plus.org/zh-CN/)
- [Spring Boot 3.5 文档](https://docs.spring.io/spring-boot/3.5/)

模型与第三方依赖遵循各自许可证；模型来源见 `face-service/models/README.md`。
