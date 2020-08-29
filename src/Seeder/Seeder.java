package Seeder;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import ChunkObject.ChunkFileObj;


public class Seeder {
	static int chunkcount;//part0~part n
	static String location = "C:/myTorrent/Seeder"; //���� directory������ �����ʿ�
	static String chunkslocation = location+"/chunks";
	static ArrayList<Integer> portlist = new ArrayList<>(); //port list
	static ArrayList<String> iplist = new ArrayList<>(); //ip list
	static int myPort; //�� ������Ʈ
	Set<String> chunkmap= Collections.synchronizedSet(new HashSet<>()); //���� ûũ��
	static String originName="";
	ServerSocket sersoc = null;
	Socket connectSoc;

	public static void main(String[] args) {
		////////////////main Thread���� �۾��� �͵�//////////////////
		new File(location+"/incoming").mkdirs();
		Seeder sharedArea = new Seeder();
		sharedArea.divideFile(); //������ ������.
		sharedArea.makeChunkmap(); //�̸����� �� ûũ���� �����.
		sharedArea.setPortNum();
		System.out.println("ûũ "+chunkcount+"�� ������");
		////////////////SERVER THREAD//////////////////
		serThread ser = new serThread(myPort);
		clientSet clileech = new clientSet();

		ser.seeder=sharedArea;
		clileech.seed=sharedArea; //Ŭ�� ����� ��
		ser.start();
		clileech.start(); //Ŭ���̾�Ʈ ������ ����
	}
	//���Ϻ���
	public void divideFile() {
		String outDir = location+"/chunks";
		new File(outDir).mkdir();
		File[] temp = new File(location+"/origin_file").listFiles();//�������� �б�
		File originFile = temp[0]; //���� ���� �б�
		originName = originFile.getName();
		System.out.println("���ϸ�: "+originFile.getName());
		try {
			System.out.println("ũ��: "+originFile.length()+" bytes"); //���� ũ��
			byte[] chunk = new byte[10240]; //10KB
			FileInputStream fis= new FileInputStream(originFile);
			BufferedInputStream bis = new BufferedInputStream(fis);
			
			int chunkread; //ûũ�� �󸶳� �о����� (������ ûũ�� ���� ���̰� �ٸ� �� ����)
			while((chunkread=bis.read(chunk))>0) { //�˾Ƽ� offset ����
				File outChunk = new File(outDir,String.format("%06d", chunkcount)+".part");
				FileOutputStream fos = new FileOutputStream(outChunk);
				BufferedOutputStream bos = new BufferedOutputStream(fos);
				bos.write(chunk,0,chunkread); //������ ������ �ڸ��� (������ ûũ�� �ٸ��ϱ�)
				bos.close();
				chunkcount++;
			}
			bis.close();
		} catch (Exception e) { //exception ��� ó��
			e.printStackTrace();
		}
	}
	//���� ûũ�� �����
	public void makeChunkmap() {

		File[] files = new File(location+"/chunks").listFiles();
		for(int i=0;i<files.length;i++)
			chunkmap.add(files[i].getName());
	}
	//portnum list �����
	public void setPortNum() {
		String str;
		BufferedReader br;
		try {
			br = new BufferedReader(new FileReader("C:/myTorrent/configuration.txt"));
			while((str=br.readLine()) != null) {
				String[] tokens = str.split(" ");
				iplist.add(tokens[0]);
				portlist.add(Integer.parseInt(tokens[1]));
			}
			iplist.remove(0); //�ڽ� ip ����
			myPort = portlist.get(0); //�� port
			portlist.remove(0);//�ڽ� port ����
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	//������Ʈ�� ����ȭ
}
//����(����) ������
class serThread extends Thread{
	int serPort;
	
	serThread(int sernum){
		this.serPort=sernum;
	}
	static ServerSocket soc = null;
	static OutputStream os = null;
	static InputStream is = null;
	static ObjectOutputStream oos = null;
	static ObjectInputStream ois = null;
	Seeder seeder = new Seeder();
	String location = Seeder.location;
	public void run() {
		try {
			soc = new ServerSocket(serPort);
			while(true){
				Socket connectSoc = soc.accept(); //socket�� ��ٸ���.
				os = connectSoc.getOutputStream();
				is = connectSoc.getInputStream();
				oos = new ObjectOutputStream(os);
				ois = new ObjectInputStream(is);
				for(int i=0;i<3;i++) { //�ִ� 3���� ûũ 
					oos.writeObject(seeder.chunkmap); //1. ������ ûũ���� ������, �ƹ��͵� ������ null���� �� ��
					int flag = (int) ois.readObject(); //2. Ŭ��� �ڽ��� �����ʰ� ���ϰ�, ���� ���� ������ 0�� ������ ��
					if(flag==0)
						break; //for�� Ż��
					//Ŭ�󿡰� �� ������ �ִ� ���
					String require = (String)ois.readObject();//3. Ŭ�� �䱸�ϴ� ûũ�� �̸�
					ChunkFileObj sendObj = makeChunkfileObj(requiredChunk(require)); //4. ���ϴ� ûũ������
					oos.writeObject(sendObj);
					if(sendObj==null)
						break;
					System.out.println("���� �۽�--> "+sendObj.getFilename()+" "+connectSoc);
				}
				connectSoc.close(); //��� ���� ������ �� Ŭ���̾�Ʈ�� ���� ���� �� �� ������ ã��
			}
		}catch(Exception e) {
			e.printStackTrace();
		}
	}
	public File requiredChunk(String request) {
		File files[] = new File(location+"/chunks").listFiles();
		int res=-1;
		for(int i=0;i<files.length;i++)
			if((files[i].getName()).equals(request)) {
				res = i;
				break;
			}
		if(res==-1)
			return null;
		else
			return files[res]; //ûũ���� ���ؼ� �ִ��� �̹� Ȯ���߱� ������ ����ó�� �ʿ����� ����
	}
	public ChunkFileObj makeChunkfileObj(File file) throws Exception {
		if(file==null)
			return null;
		
		ChunkFileObj objchunk = new ChunkFileObj();
		byte[] chunk = new byte[10240];
		
		FileInputStream fis = new FileInputStream(file);
		BufferedInputStream bis = new BufferedInputStream(fis);
		int chunksize = bis.read(chunk);
		
		objchunk.setFilename(file.getName()); //��ü�� �̸� ����
		objchunk.setChunksize(chunksize);//��ü�� ũ�� ����
		objchunk.setFiledata(chunk);//��ü�� ������ ����
		objchunk.setChunkcount(seeder.chunkcount);
		objchunk.setOriginName(seeder.originName);
		bis.close();
		fis.close();
		
		return objchunk;
	}
}
//Ŭ���̾�Ʈ�� �����ϴ� ������
class clientSet extends Thread{ 
	Seeder seed = new Seeder();
	public void run() {
		
		cliThread c1 = new cliThread();
		cliThread c2 = new cliThread();
		cliThread c3 = new cliThread();
		c1.seed = seed;
		c2.seed = seed;
		c3.seed = seed;
		
		c1.start();
		c2.start();
		c3.start();
		try {
			//�� �������� ���Ḧ ��ٸ�
			c1.join();
			c2.join();
			c3.join();
			mergeFiles(Seeder.originName);
			System.out.println("File download completed!");
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	
	}
	//���� ��ġ��
	public void mergeFiles(String originFileName) {
		File[] files = new File(Seeder.chunkslocation).listFiles();
		byte[] chunk = new byte[10240];
		try {
			FileOutputStream fos = new FileOutputStream(new File(Seeder.location+"/incoming/"+originFileName));
			
			for(File f: files) {
				FileInputStream fis = new FileInputStream(f);
				BufferedInputStream bis = new BufferedInputStream(fis);
				int fileRead = 0;
				while((fileRead = bis.read(chunk))>-1)
					fos.write(chunk, 0, fileRead);
			}
			fos.flush();
			fos.close();
		}catch(Exception e) {
			e.printStackTrace();
		}
	}
}
//Ŭ���̾�Ʈ ������
class cliThread extends Thread{
	Seeder seed; //leecher1�� ���� ��� ����
	OutputStream os = null;
	InputStream is = null;
	ObjectOutputStream oos = null;
	ObjectInputStream ois = null;
	int timeout = 10000; //Ÿ�Ӿƿ� 10��
	static Set<String> requiremap = Collections.synchronizedSet(new HashSet<>());; //�ܼ� �����, �� �� ���ῡ �� ���� �ʿ�
	Random random = new Random();
	public void run() {
		while(true) {
			try {
				while(true) {
					int randomIndex = random.nextInt(Seeder.iplist.size());
					SocketAddress socketAddress = new InetSocketAddress(Seeder.iplist.get(randomIndex), Seeder.portlist.get(randomIndex));
					Socket soc = new Socket();
					soc.setSoTimeout(timeout);
					soc.connect(socketAddress, timeout);
					is = soc.getInputStream();
					ois = new ObjectInputStream(is);
					os = soc.getOutputStream();
					oos = new ObjectOutputStream(os);
					
					for(int i=0;i<3;i++) {
						Set<String> serChunkmap = (Set<String>) ois.readObject(); //1.�����κ��� ûũ�� �ޱ�
						String requiring = filerequire(serChunkmap,seed.chunkmap);
						if(requiremap.size()==0) {
							oos.writeObject(0); //2.�ʿ��� ������ ���� ��� flag 0 �����ֱ�
							break; //for�� Ż��
						}
						//��û�� ������ �ִ� ���
						oos.writeObject(1);
						oos.writeObject(requiring);//3. ������ �ʿ����� string ������
						ChunkFileObj rcvObj =  (ChunkFileObj) ois.readObject();//4. ûũ ����
						if(rcvObj==null)
							break;
						Seeder.chunkcount = rcvObj.getChunkcount(); //��ü ���� ���� ����
						Seeder.originName = rcvObj.getOriginName(); //���� ���ϸ� ����
						System.out.println("���� ����<-- "+rcvObj.getFilename()+" "+soc);

						saveFile(rcvObj,seed.chunkmap);
						sleep(1000);
					}
					soc.close(); //��� �� �������Ƿ�, ���� ��� ����
					System.out.println(seed.chunkmap.size()+" / "+Seeder.chunkcount);
					if(seed.chunkmap.size()==Seeder.chunkcount)
						break;
				}
			}catch (ConnectException e1) { //���� ���� �߻� ��, �ٽ� ����
				System.out.println("����Ȯ�� �� �ٸ� ���� ã��...");
			}catch(SocketException e2) {
				System.out.println("���� Ȯ�� �� �ٸ� ���� ã��...");
			}catch(TimeoutException e3) {
				System.out.println("���� Ȯ�� �� �ٸ� ���� ã��...");
			}
			catch(Exception e) {
				e.printStackTrace();
			}
			if((seed.chunkmap.size()==Seeder.chunkcount)&&(Seeder.chunkcount!=0)) {
				System.out.println("Download "+Thread.currentThread().getName()+" ����");
				break; //while�� Ż���ϰ� �Լ� ����
			}
		}
	}
	public synchronized String filerequire(Set<String> sermap,Set<String> mymap) {
		requiremap = sermap;
		Object[] mylist = mymap.toArray();
		if(requiremap.size()==0)
			return null;
		if(mylist.length==0) {
			int requireNum = random.nextInt(requiremap.size());
			Object[] requiring = requiremap.toArray();
			seed.chunkmap.add((String) requiring[requireNum]);
			return (String) requiring[requireNum];
		}
		else {
			for(int i=0;i<mylist.length;i++) {
				if(sermap.contains(mylist[i]))
					requiremap.remove(mylist[i]);
			}
				if(requiremap.size()==0)
					return null;
				else {
					int requireNum = random.nextInt(requiremap.size());
					Object[] requiring = requiremap.toArray();
					seed.chunkmap.add((String) requiring[requireNum]);
					return (String) requiring[requireNum];
				}
		}
	}
	//���� ������ ���� ���ü��� ���ؾ��Ѵ�.
	public void saveFile(ChunkFileObj obj, Set<String> chunkmap) throws Exception {
		int chunksize = obj.getChunksize();
		String chunkname = obj.getFilename();
		byte[] chunkdata = obj.getFiledata();
		
		FileOutputStream fos = new FileOutputStream(new File(Seeder.chunkslocation,chunkname));
		BufferedOutputStream bos = new BufferedOutputStream(fos);
		bos.write(chunkdata, 0, chunksize);
		bos.close();
		//chunkmap.add(chunkname);//���� ������Ʈ�� �̸����� chunkmap update
	
	}
}

