# Ani-RSS 与 OpenList 的兼容路线评估

基线：Ani-RSS `v3.2.38`。本文只评估实现路线，不改变现有下载器或用户数据。

## 结论

优先在本分支维护现有的 `OpenList` 下载器。将 OpenList 包装成 Aria2 JSON-RPC 服务虽然可以接收下载任务，但无法只靠外部插件保留 Ani-RSS 的重命名、完成通知和部分资源格式行为。若仍需修改 Ani-RSS，直接维护原生下载器的代码面更小，现有配置也可以原样沿用。

## 路线 A：Aria2 JSON-RPC 兼容服务

Ani-RSS 的 `Aria2` 后端调用 `getGlobalStat`、`addTorrent`、`tellActive`、`tellWaiting`、`tellStopped`、`removeDownloadResult` 和 `changeGlobalOption`。兼容服务需要把这些方法映射到 OpenList API，并持久保存 Aria2 GID 与 OpenList 任务 ID 的对应关系。查询任务时，还必须构造 Ani-RSS 预期的 `bittorrent.info.name`、`dir`、`files`、`infoHash` 和状态。

下载器选择由 `ConfigService` / `CronConfig` 按 `ani.rss.download.<名称>` 加载 Spring Bean；当前没有可独立安装的下载器插件接口。这里的“插件”实际只能是对外冒充 Aria2 的 RPC 服务。

仅实现 RPC 仍有以下缺口：

1. `Aria2.download()` 拒绝 `.txt` 磁力链接；现有 `OpenList.download()` 会从种子取得磁力链接后提交到 OpenList。
2. `Aria2.rename()` 只在完成后操作本地 `File`，并检查文件存在和大小；现有 OpenList 后端通过 OpenList API 重命名、移动云端文件。RPC 服务无法改变 Ani-RSS 的这段本地文件逻辑。
3. `Aria2.addTags()` 固定返回 `false`，而通用下载完成通知需要先成功添加完成标签；因此外部 RPC 服务无法保持现有 Ani-RSS 完成通知行为。
4. `Aria2.setSavePath()` 不执行操作，且删除任务只调用 `removeDownloadResult`，与 OpenList 的云端任务及文件语义不同。

若选择此路线，仍需修改 Ani-RSS 的 Aria2 后端，并维护一个带持久状态的 RPC 服务。它不再是独立于 Ani-RSS 的简单插件。CloudDrive 等挂载可能让本地文件重命名碰巧可用，但不能作为等价实现的前提。

## 路线 B：维护原生 OpenList 下载器

`v3.2.38` 已有 `OpenList.java`、`OpenListUtil.java`、OpenList 配置和任务/文件实体。下载流程已经处理提交离线任务、等待状态、重试、云端重命名、移动文件及完成通知。沿用此路线不需要转换既有的 `config.v2.json` 和 `ani.v2.json`。

需要重点维护和验证的部分：

1. 保留下载器选择入口及相关配置字段，避免合并上游改动时把 OpenList 实现或 UI 选项删掉。
2. 给任务状态轮询设置间隔或退避；当前 `while (true)` 在任务信息为空等路径可能密集请求。
3. 区分提交成功、任务成功、文件实际可见、重命名与移动成功；失败时保留可诊断信息，并避免重复通知或重复下载。
4. 针对 OpenList API 的成功、失败、取消、任务暂不可见及文件延迟可见编写隔离测试，再用非生产数据做一次端到端验证。
5. 为自建镜像使用固定版本或摘要，并在合并上游版本时运行兼容检查。上游移除 OpenList 后，后续合并可能产生冲突，需要由本分支维护。

## 源码依据

- `ani-rss-application/src/main/java/ani/rss/download/Aria2.java`
- `ani-rss-application/src/main/java/ani/rss/entity/torrent/Aria2RpcBody.java`
- `ani-rss-application/src/main/java/ani/rss/entity/torrent/Aria2TorrentsInfo.java`
- `ani-rss-application/src/main/java/ani/rss/task/RenameTask.java`
- `ani-rss-application/src/main/java/ani/rss/service/DownloadService.java`
- `ani-rss-application/src/main/java/ani/rss/service/ConfigService.java`
- `ani-rss-application/src/main/java/ani/rss/config/CronConfig.java`
- `ani-rss-application/src/main/java/ani/rss/download/OpenList.java`
- `ani-rss-application/src/main/java/ani/rss/util/other/OpenListUtil.java`

本分支从 `v3.2.38` 建立。当前提交只记录评估，不部署到 NAS。
