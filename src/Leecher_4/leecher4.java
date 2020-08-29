package Leecher_4;

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
import Seeder.Seeder;

public class leecher4 {
	static String location = "C:/myTorrent/Leech_4";
	static String chunkslocation = location+"/chunks";
	static ArrayList<Integer> portlist = new ArrayList<>(); //port list
	static ArrayList<String> iplist = new ArrayList<>(); //ip list
	static int myPort; //�� ������Ʈ
	Set<String> chunkmap= Collections.synchronizedSet(new HashSet<>()); //���� ûũ��
	static String originName="";
	static int chunkcount;
	
	public static void main(String[] args) {
		new File(chunkslocation).mkdirs(); // chunks/incoming���� ���� ����
		new File(location+"/incoming").mkdirs();
		setPortNum();
		leecher4 sharedArea = new leecher4(); //�Ǿ��� ������, ����, Ŭ�� ��� �����ؼ� ����ؾ���
		serThread serleech = new serThread(myPort);
		clientSet clileech = new clientSet();

		serleech.leech=sharedArea; //������ ����� ��
		clileech.leech=sharedArea; //Ŭ�� ����� ��
		serleech.start(); //���� ������ ����
		clileech.start(); //Ŭ���̾�Ʈ ������ ����
	}
	
	public static void setPortNum() {
		String str;
		BufferedReader br;
		try {
			br = new BufferedReader(new FileReader("C:/myTorrent/configuration.txt"));
			while((str=br.readLine()) != null) {
				String[] tokens = str.split(" ");
				iplist.add(tokens[0]);
				portlist.add(Integer.parseInt(tokens[1]));
			}
			iplist.remove(4); //�ڽ� ip ����
			myPort = portlist.get(4); //�� port
			portlist.remove(4);//�ڽ� port ����
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
//Ŭ���̾�Ʈ�� �����ϴ� ������
class clientSet extends Thread{ 
	leecher4 leech = new leecher4();
	public void run() {
		
		cliThread c1 = new cliThread();
		cliThread c2 = new cliThread();
		cliThread c3 = new cliThread();
		c1.leech = leech;
		c2.leech = leech;
		c3.leech = leech;
		
		c1.start();
		c2.start();
		c3.start();
		try {
			//�� �������� ���Ḧ ��ٸ�
			c1.join();
			c2.join();
			c3.join();
			mergeFiles(leecher4.originName);
			System.out.println("File download completed!");
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	
	}
	//���� ��ġ��
	public void mergeFiles(String originFileName) {
		File[] files = new File(leecher4.chunkslocation).listFiles();
		byte[] chunk = new byte[10240];
		try {
			FileOutputStream fos = new FileOutputStream(new File(leecher4.location+"/incoming/"+originFileName));
			
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
	leecher4 leech; //leecher1�� ���� ��� ����
	OutputStream os = null;
	InputStream is = null;
	ObjectOutputStream oos = null;
	ObjectInputStream ois = null;
	int timeout = 10000;
	static Set<String> requiremap = Collections.synchronizedSet(new HashSet<>());; //�ܼ� �����, �� �� ���ῡ �� ���� �ʿ�
	Random random = new Random();
	public void run() {
		while(true) {
			try {
				while(true) {
					int randomIndex = random.nextInt(leecher4.iplist.size());
					SocketAddress socketAddress = new InetSocketAddress(leecher4.iplist.get(randomIndex), leecher4.portlist.get(randomIndex));
					Socket soc = new Socket();
					soc.setSoTimeout(timeout);
					soc.connect(socketAddress, timeout);
					is = soc.getInputStream();
					ois = new ObjectInputStream(is);
					os = soc.getOutputStream();
					oos = new ObjectOutputStream(os);
					
					for(int i=0;i<3;i++) {
						Set<String> serChunkmap = (Set<String>) ois.readObject(); //1.�����κ��� ûũ�� �ޱ�
						String requiring = filerequire(serChunkmap,leech.chunkmap);
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
						leecher4.chunkcount = rcvObj.getChunkcount(); //��ü ���� ���� ����
						leecher4.originName = rcvObj.getOriginName(); //���� ���ϸ� ����
						System.out.println("���� ����<-- "+rcvObj.getFilename()+" "+soc);

						saveFile(rcvObj,leech.chunkmap);
						sleep(1000);
					}
					soc.close(); //��� �� �������Ƿ�, ���� ��� ����
					System.out.println(leech.chunkmap.size()+" / "+leecher4.chunkcount);
					if(leech.chunkmap.size()==leecher4.chunkcount)
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
			if((leech.chunkmap.size()==leecher4.chunkcount)&&(leecher4.chunkcount!=0)) {
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
			leech.chunkmap.add((String) requiring[requireNum]);
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
					leech.chunkmap.add((String) requiring[requireNum]);
					return (String) requiring[requireNum];
				}
		}
	}
	//���� ������ ���� ���ü��� ���ؾ��Ѵ�.
	public void saveFile(ChunkFileObj obj, Set<String> chunkmap) throws Exception {
		int chunksize = obj.getChunksize();
		String chunkname = obj.getFilename();
		byte[] chunkdata = obj.getFiledata();
		
		FileOutputStream fos = new FileOutputStream(new File(leecher4.chunkslocation,chunkname));
		BufferedOutputStream bos = new BufferedOutputStream(fos);
		bos.write(chunkdata, 0, chunksize);
		bos.close();
		//chunkmap.add(chunkname);//���� ������Ʈ�� �̸����� chunkmap update
	
	}
}
//���� ������, ���� ������� ������� �ʴ´�.
class serThread extends Thread{
	static ServerSocket soc = null;
	static OutputStream os = null;
	static InputStream is = null;
	static ObjectOutputStream oos = null;
	static ObjectInputStream ois = null;
	int serPort; //������Ʈ
	leecher4 leech; //leecher4 ���� ���
	String location = leecher4.location;
	//������
	serThread(int serPort){
		this.serPort = serPort;
	}
	//Thread ����
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
					oos.writeObject(leech.chunkmap); //1. ������ ûũ���� ������, �ƹ��͵� ������ null���� �� ��
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
			return files[res];
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
		objchunk.setChunkcount(leecher4.chunkcount);
		objchunk.setOriginName(leecher4.originName);
		bis.close();
		fis.close();
		
		return objchunk;
	}
}
