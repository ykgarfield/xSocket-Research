一个连接：
1. 一个 NonBlockingConnection(父类：AbstractNonBlockingStream)

2. AbstractNonBlockingStream 中的实例变量：
ReadQueue：读队列
WriteQueue：写队列


处理连接任务：
IoSocketDispatcher#register(IoSocketHandler, int)
根据isDispatcherInstanceThread分为两种情况：
1.

2.建立一个 IoSocketDispatcher.RegisterTask 任务.并加入到 registerQueue 中.
registerQueue为 ConcurrentLinkedQueue 类型

performRegisterHandlerTasks()方法中从registerQueue取出一个RegisterTask任务.
然后执行RegisterTask.run()线程.

RegisterTask.run()方法中执行registerHandlerNow()方法：
首先注册OP_READ事件,然后执行 IoSocketHandler#onRegisteredEvent()
=> NonBlockingConnection.IoHandlerCallback#onConnect()
=> NonBlockingConnection#onConnect()
=> HandlerAdapter.onConnect()
建立PerformOnConnectTask任务,交由线程池去处理.


读OP_READ任务：
IoSocketHandler.onReadableEvent()处理：
数据的读取是同步, 全部读取完之后, 交由线程去执行onData()方法.
