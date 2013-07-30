package serializableObj;

import java.io.Serializable;

public class Msg implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private int tel;
	private String name;
	private String content;

	public Msg(int tel, String name, String content) {
		super();
		this.tel = tel;
		this.name = name;
		this.content = content;
	}

	public int getTel() {
		return tel;
	}

	public void setTel(int tel) {
		this.tel = tel;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	@Override
	public String toString() {
		return "Msg [tel=" + tel + ", name=" + name + ", content=" + content + "]";
	}

}
