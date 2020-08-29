package ChunkObject;

public class ChunkFileObj implements java.io.Serializable{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private String filename;
	private byte[] filedata;
	private int chunksize;
	private int chunkcount;
	private String originName;
	
	public String getOriginName() {
		return originName;
	}
	public void setOriginName(String originName) {
		this.originName=originName;
	}
	public String getFilename(){
		return filename;
	}
	public void setFilename(String filename) {
		this.filename=filename;
	}
	public byte[] getFiledata() {
		return filedata;
	}
	public void setFiledata(byte[] filedata) {
		this.filedata=filedata;
	}
	public int getChunksize() {
		return chunksize;
	}
	public void setChunksize(int chunksize) {
		this.chunksize = chunksize;
	}
	public int getChunkcount() {
		return chunkcount;
	}
	public void setChunkcount(int chunkcount) {
		this.chunkcount = chunkcount;
	}
}

