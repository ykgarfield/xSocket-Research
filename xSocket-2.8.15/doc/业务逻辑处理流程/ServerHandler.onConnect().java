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
=> HandlerAdapter#onConnect()
=> HandlerAdapter#performOnConnect(INonBlockingConnection, SerializedTaskQueue, IConnectHandler)
	(// 这里参数中的IConnectHandler也就是我们实现的业务逻辑处理类ServerHandler)
=> handler.onConnect(connection);
	(// 执行ServerHandler.onConnect(connection)).
			
			