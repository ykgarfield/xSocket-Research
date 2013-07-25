public class ServerHandler implements IDataHandler, IConnectHandler,
		IIdleTimeoutHandler, IConnectionTimeoutHandler, IDisconnectHandler


   IConnectHandler#onConnect()执行流程：
   Server#start()
=> Server#run()
=> acceptor#listen() (IoAcceptor#listen())
=> IoAcceptor#accept()
=> Server.LifeCycleHandler#onConnectionAccepted(IoSocketHandler)
=> Server.LifeCycleHandler#init(NonBlockingConnection connection, IoChainableHandler ioHandler)
=> NonBlockingConnection#init(IoChainableHandler ioHandler)
=> IoSocketHandler#init(IoSocketHandler)
=> IoSocketDispatcher#register(IoSocketHandler socketHandler, int ops)
=> IoSocketDispatcher#registerHandlerNow(IoSocketHandler socketHandler, int ops)
=> IoSocketHandler#onRegisteredEvent()
=> NonBlockingConnection.IoHandlerCallback#onConnect()
=> NonBlockingConnection#onConnect();
=> HandlerAdapter#onConnect(final INonBlockingConnection connection, final SerializedTaskQueue taskQueue, final Executor workerpool, boolean isUnsynchronized)
=> taskQueue.performMultiThreaded(Runnable task, Executor workerpool)及执行的
   taskQueue.performMultiThreaded(new PerformOnConnectTask(connection, taskQueue, (IConnectHandler) handler), workerpool);
	(// 这里参数中的handler也就是我们实现的业务逻辑处理类ServerHandler)
=> handler.onConnect(connection);
	(// 执行ServerHandler.onConnect(connection)).
			
			