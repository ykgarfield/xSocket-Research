public class ServerHandler implements IDataHandler, IConnectHandler,
		IIdleTimeoutHandler, IConnectionTimeoutHandler, IDisconnectHandler

   IDataHandler#onConnect()执行流程：
		
=> IoSocketHandler.onReadableEvent()
=> getPreviousCallback().onPostData()
=> NonBlockingConnection.IoHandlerCallback.onPostData()
=> NonBlockingConnection.onPostData()
=> HandlerAdapter.onData(INonBlockingConnection, SerializedTaskQueue, Executor, boolean, boolean)
=> HandlerAdapter.performOnData(INonBlockingConnection, SerializedTaskQueue, boolean, IDataHandler)
	(这里参数中的IDataHandler也就是我们实现的业务逻辑处理类ServerHandler)
=> handler.onData(connection);
	(执行ServerHandler.onData(connection)).
		
		