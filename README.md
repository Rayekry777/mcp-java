# MCP SSE 与第三方 stdio 示例

业务 `mcp-server` 使用 SSE，独立监听 8090；`mcp-client` 监听 8080，连接业务 SSE Server、filesystem 和 GitHub。第三方 Server 继续使用 stdio。

## Inspector 同时测试 SSE 和 stdio

同一个 jar 支持两种配置，分别运行两个进程。默认是 SSE；传入 `--spring.profiles.active=stdio` 切换为 stdio。业务工具、资源和提示词共用同一份代码。

先打包 `mvn package`。Inspector 添加两条服务器记录：

| 配置项 | SSE 记录 | stdio 记录 |
|---|---|---|
| 服务器 ID | `ray-sse` | `ray-stdio` |
| 运输 | SSE | stdio |
| URL | `http://localhost:8090/sse` | 不需要 |
| Command | 不需要 | `D:/JDK21/bin/java.exe` |
| 自定义请求头 | `X-API-Key: local-mcp-test-key`，与服务端配置一致 | 不需要 |

SSE 需要先在 IDEA 启动 `McpServerApplication`，或执行 `java -jar mcp-server/target/mcp-server-1.0.0.jar`。stdio 由 Inspector 自动启动，**命令栏只填 java 可执行文件**，参数每行一个：

```text
-jar
D:/JavaPro/mcp/mcp/mcp-server/target/mcp-server-1.0.0.jar
--spring.profiles.active=stdio
```

`-jar` 是 JVM 参数，`--spring.profiles.active=stdio` 是应用参数，必须放在 jar 路径后面。stdio 不启动 Tomcat，不占用 8090，日志写入 `mcp-server/mcp-server-stdio.log`，SSE 仍写入 `mcp-server/mcp-server.log`。Inspector 连接两条记录后，都能测试 `getDateInfo` 等业务工具。

## 环境与启动步骤

- Java 21、Maven。
- Node.js 与 npm/npx；建议使用当前 Node.js LTS（本机已用 Node.js 24 验证）。
- 默认启用远程 SSE 连接和 filesystem，GitHub 及本地 jar 子进程连接默认关闭。

先在项目根目录构建：

```powershell
mvn clean package
```

先启动 `McpServerApplication`，或在一个终端执行：

```powershell
java -jar mcp-server/target/mcp-server-1.0.0.jar
```

出现 `Tomcat started on port 8090` 后，再启动 `McpClientApplication`，或在另一个终端执行：

```powershell
java -jar mcp-client/target/mcp-tools-client-1.0.0.jar
```

Server 不再由 Client 拉起。`MCP_REMOTE_ENABLED` 默认是 `true`；IDEA 中若曾设置成 `false`，请删除旧变量或改为 `true`。地址可通过 `MCP_REMOTE_BASE_URL` 修改，SSE 路径可通过 `MCP_REMOTE_SSE_ENDPOINT` 修改。

验证 SSE 端点（框架自动提供，无需 Controller）：

```powershell
curl.exe -N http://localhost:8090/sse
```

收到 `event:endpoint` 与 `/mcp/messages?sessionId=...` 后持续等待是正常现象，按 Ctrl+C 退出。验证工具列表和业务调用：

```powershell
Invoke-RestMethod 'http://localhost:8080/api/remote-mcp/tools'
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/remote-mcp/call?toolName=getDateInfo' -ContentType application/json -Body '{}'
```

Agent 的 `/api/mcp/tools` 同时汇总远程工具，名称带 `remote__` 前缀。

只测试第三方 Server 时，在 IDEA 的 `McpClientApplication` Run Configuration → Environment variables 中设置：

```text
MCP_LOCAL_ENABLED=false
MCP_REMOTE_ENABLED=false
MCP_FILESYSTEM_ALLOWED_DIRECTORY=D:/JavaPro/mcp/mcp-stdio
```

然后启动 `McpClientApplication`，或者在项目根目录执行：

```powershell
java -jar mcp-client/target/mcp-tools-client-1.0.0.jar --mcp.remote.enabled=false --mcp.local.enabled=false
```

客户端默认监听 8080 端口。filesystem 默认授权启动客户端时的工作目录（`${user.dir}`）；上面的环境变量可改为其他已存在目录。多个授权目录请修改 `mcp-client/src/main/resources/application.yml` 的 `allowed-directories` 列表。授权范围内的工具支持读写操作。

手动确认 Node.js 和 filesystem Server 可以运行：

```powershell
node --version
npx.cmd -y @modelcontextprotocol/server-filesystem "D:/JavaPro/mcp/mcp-stdio"
```

macOS/Linux 把 `npx.cmd` 换成 `npx`。进程启动后等待 stdin 输入是正常现象，按 Ctrl+C 退出。首次启动需要下载 npm 包，客户端初始化超时默认 120 秒，可在配置中调整。

Java 子进程继承启动 IDEA/Java 时的 PATH。Windows 自动使用 `cmd.exe /c npx.cmd`，macOS/Linux 使用 `npx`。若 IDEA 找不到命令，在 Run Configuration 中添加 `MCP_NPX_COMMAND`，值为 npx 的绝对路径，例如本机的 `D:/aitool/node/npx.cmd`；macOS 可用 `command -v npx` 查询。路径不需要自行包裹引号，参数由 Java 分开传递。

## filesystem 接口

列出工具：

```powershell
Invoke-RestMethod 'http://localhost:8080/api/third-party/filesystem/tools'
```

列出授权目录的文件：

```powershell
$body = @{ path = 'D:/JavaPro/mcp/mcp-stdio' } | ConvertTo-Json
Invoke-RestMethod -Method Post `
  -Uri 'http://localhost:8080/api/third-party/filesystem/call?toolName=list_directory' `
  -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body))
```

读取文件（先用 tools 接口确认当前 Server 提供的工具名）：

```powershell
$body = @{ path = 'D:/JavaPro/mcp/mcp-stdio/pom.xml' } | ConvertTo-Json
Invoke-RestMethod -Method Post `
  -Uri 'http://localhost:8080/api/third-party/filesystem/call?toolName=read_text_file' `
  -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body))
```

## GitHub 接入

为兼容原示例，默认启动命令仍是 `npx -y @modelcontextprotocol/server-github`。**该 npm 包已弃用**，上游开发已迁移至 [GitHub 官方 Server](https://github.com/github/github-mcp-server)；参见[旧版弃用公告](https://github.com/modelcontextprotocol/servers-archived/tree/main/src/github)。

使用旧版 npm 示例时，在 GitHub Settings → Developer settings → Personal access tokens 创建满足所需操作权限的 PAT，然后在 IDEA 的环境变量中添加：

```text
MCP_GITHUB_ENABLED=true
GITHUB_TOKEN=你自己的Token
```

不要将真实 Token 提交到代码或配置文件。也支持读取 `GITHUB_PERSONAL_ACCESS_TOKEN`；客户端会以该变量名传给 GitHub Server。只测试 filesystem 无需 Token，也无需注释 Bean 或修改构造参数。

查询工具与搜索公开仓库：

```powershell
Invoke-RestMethod 'http://localhost:8080/api/third-party/github/tools'
$body = @{ query = 'modelcontextprotocol'; perPage = 5 } | ConvertTo-Json
Invoke-RestMethod -Method Post `
  -Uri 'http://localhost:8080/api/third-party/github/call?toolName=search_repositories' `
  -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body))
```

如已安装官方 `github-mcp-server` 可执行文件，可将 YAML 中的 GitHub 配置替换为（Windows 路径按实际安装位置修改）：

```yaml
mcp:
  third-party:
    github:
      enabled: true
      command: D:/tools/github-mcp-server.exe
      args:
        - stdio
```

此时仍通过环境变量配置 Token。工具名和参数以所连接版本的 `/github/tools` 结果及对应 Server 文档为准。

## 开关、错误与测试

- `MCP_FILESYSTEM_ENABLED=false`：关闭 filesystem。
- `MCP_GITHUB_ENABLED=false`：关闭 GitHub（默认值）。
- `MCP_LOCAL_ENABLED=false`：关闭本地 jar 连接及其 Controller，不影响第三方接口。
- 未启用的第三方 Server 返回 HTTP 503，并在 JSON 的 `detail` 中说明配置方式。
- 工具返回 `isError=true` 时，接口返回 HTTP 502，并保留工具错误文本；成功时返回所有文本内容，以换行连接。
- 启用 GitHub 却未提供 Token，或者 filesystem 授权目录不存在，会在启动时给出明确错误。应用关闭时自动关闭第三方 MCP 客户端。

运行客户端自动测试（不依赖 Node.js、外部 Server 或真实 Token）：

```powershell
mvn -pl mcp-client test
```
