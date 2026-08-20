你是一个合格的java开发工程师、全栈开发工程师，你懂的java开发和Vue3开发的规范注意事项。
本次代码编写均围绕 QQ 官方机器人端群场景进行开发，与Napcat Discord QQGuild等无关系。
开发主要内容：群管系统。包括禁言策略，关键词违规撤回策略，加群审核策略，注意：加群审核是指我们根据加群事件框架自己判定，而不是用机器人的策略审核自动通过，请注意区分！
首先，相关功能需要制作webui，且需要新开一个TAB，相关内容在 @webui 中，群管机制在 @chat 中，webui后端在 @src/main/java/top/yzljc/atribot/webui 下，群管机制在 @src/main/java/top/yzljc/atribot/chat 下。

开发核心1：违规词/关键词撤回策略
需要制作：关键词命中撤回，关键类型命中撤回，比如链接，小程序等，允许自行填入词，设置规则，比如 包含（默认） 完全相等，然后，还写一个 AI 聊天审核策略，
要求：前端控制审核的提示词，后端的相关AI逻辑在 @service 中有提到，记得开异步线程，不然消息这么多卡死我。
对于违规词的撤回，需要有多种模式，不同的模式，AI的和纯词汇命中样式的分开写，就是AI模式和词汇命中模式可以各自设置各自的
包括：撤回后的提醒信息（比如发消息：你的消息违规了哦），是否撤回，是否禁言，禁言时长，是否通知到Debug群（注意，通知到debug群，用 Alert.nofity 方法发送即可），
AI的也有一套完全相同的设置，总之，这里尽可能的详细，给能做到的都做，群管有哪些，你作为一个非常精明的开发者和QQ用户，你一定知道

开发核心2：加群自动审核
需要制作：检测到关键词自动同意/拒绝，关键词机制同上违规词撤回 | AI审核，同样的，AI的系统提示词等需要前端可编辑
同样的，可选是否通知到debug群，这俩规则也是可选是否各自启用（启用A B或者AB或者all disabled）

通知到debug群的后端代码请做TODO注释标记，后续我需要改动，禁言和撤回均是

开发建议：
像是ai的提示词，和一些前端一次性设置，建议参考[Properties.java](../src/main/java/top/yzljc/atribot/configuration/Properties.java)，写到data/xxx.json
中，不存数据库
对于群管系统执行操作所进行的日志记录，建议写到数据库中，方便后续查询和统计，建议是：撤回，禁言，加群同意/拒绝均作日志
群管系统对群进行配置时，只显示机器人是管理员的群，如果你不知道怎么获取，请参考[GroupStrategyView.vue](../webui/src/views/GroupStrategyView.vue)
前端页面：请顾及项目现有风格，不要改动与你本次编写无关的代码，上述内容均设计到一个前端TAB里，不要作好几个，前端务必兼容手机UI，建议能用svg的适当使用svg进行美化，作为优秀的
前端设计师，你不能设计的想大粪一样。

以上开发中，java的文件头请使用
/**
* @Author AndyOctopus
* @ClassName xxx
* @Created_at 2026/xxxxx
* @Project AtriMeow
* @Package top.yzljc.atribot.xxxxxx
*/
所有注释内容，不要写。句号