package serializableObj;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class SerializableUtil {
	// 序列化对象
	public static byte[] serialize(Serializable obj) throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		ObjectOutputStream oos = new ObjectOutputStream(bos);
		oos.writeObject(obj);
		oos.close();

		return bos.toByteArray();
	}

	// 反序列化对象
	public static Serializable deserialize(byte[] data) throws IOException {
		try {
			ByteArrayInputStream bis = new ByteArrayInputStream(data);
			ObjectInputStream ois = new ObjectInputStream(bis);
			return (Serializable) ois.readObject();
		} catch (ClassNotFoundException cfe) {
			throw new IOException(cfe.toString());
		}
	}

}
