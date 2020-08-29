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
	static String location = "C:/myTorrent/Seeder"; //다음 directory까지는 생성필요
	static String chunkslocation = location+"/chunks";
	static ArrayList<Integer> portlist = new ArrayList<>(); //port list
	static ArrayList<String> iplist = new ArrayList<>(); //ip list
	static int myPort; //내 서버포트
	Set<String> chunkmap= Collections.synchronizedSet(new HashSet<>()); //나의 청크맵
	static String originName="";
	ServerSocket sersoc = null;
	Socket connectSoc;

	public static void main(String[] args) {
		////////////////main Thread에서 작업할 것들//////////////////
		new File(location+"/incoming").mkdirs();
		Seeder sharedArea = new Seeder();
		sharedArea.divideFile(); //파일을 나눈다.
		sharedArea.makeChunkmap(); //이름으로 된 청크맵을 만든다.
		sharedArea.setPortNum();
		System.out.println("청크 "+chunkcount+"개 생성됨");
		////////////////SERVER THREAD//////////////////
		serThread ser = new serThread(myPort);
		clientSet clileech = new clientSet();

		ser.seeder=sharedArea;
		clileech.seed=sharedArea; //클라가 사용할 것
		ser.start();
		clileech.start(); //클라이언트 쓰레드 시작
	}
	//파일분할
	public void divideFile() {
		String outDir = location+"/chunks";
		new File(outDir).mkdir();
		File[] temp = new File(location+"/origin_file").listFiles();//원본파일 읽기
		File originFile = temp[0]; //원본 파일 읽기
		originName = originFile.getName();
		System.out.println("파일명: "+originFile.getName());
		try {
			System.out.println("크기: "+originFile.length()+" bytes"); //파일 크기
			byte[] chunk = new byte[10240]; //10KB
			FileInputStream fis= new FileInputStream(originFile);
			BufferedInputStream bis = new BufferedInputStream(fis);
			
			int chunkread; //청크를 얼마나 읽었는지 (마지막 청크만 읽은 길이가 다를 수 있음)
			while((chunkread=bis.read(chunk))>0) { //알아서 offset 전진
				File outChunk = new File(outDir,String.format("%06d", chunkcount)+".part");
				FileOutputStream fos = new FileOutputStream(outChunk);
				BufferedOutputStream bos = new BufferedOutputStream(fos);
				bos.write(chunk,0,chunkread); //사이즈 단위로 자르기 (마지막 청크는 다르니까)
				bos.close();
				chunkcount++;
			}
			bis.close();
		} catch (Exception e) { //exception 묶어서 처리
			e.printStackTrace();
		}
	}
	//서버 청크맵 만들기
	public void makeChunkmap() {

		File[] files = new File(location+"/chunks").listFiles();
		for(int i=0;i<files.length;i++)
			chunkmap.add(files[i].getName());
	}
	//portnum list 만들기
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
			iplist.remove(0); //자신 ip 제외
			myPort = portlist.get(0); //내 port
			portlist.remove(0);//자신 port 제외
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	//오브젝트로 직렬화
}
//서버(전송) 쓰레드
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
				Socket connectSoc = soc.accept(); //socket을 기다린다.
				os = connectSoc.getOutputStream();
				is = connectSoc.getInputStream();
				oos = new ObjectOutputStream(os);
				ois = new ObjectInputStream(is);
				for(int i=0;i<3;i++) { //최대 3개의 청크 
					oos.writeObject(seeder.chunkmap); //1. 서버의 청크맵을 보내줌, 아무것도 없으면 null값이 될 것
					int flag = (int) ois.readObject(); //2. 클라는 자신의 서버맵과 비교하고, 받을 것이 없으면 0을 보내줄 것
					if(flag==0)
						break; //for문 탈출
					//클라에게 줄 파일이 있는 경우
					String require = (String)ois.readObject();//3. 클라가 요구하는 청크의 이름
					ChunkFileObj sendObj = makeChunkfileObj(requiredChunk(require)); //4. 원하는 청크보내줌
					oos.writeObject(sendObj);
					if(sendObj==null)
						break;
					System.out.println("파일 송신--> "+sendObj.getFilename()+" "+connectSoc);
				}
				connectSoc.close(); //모든 일이 끝나면 그 클라이언트와 연결 종료 후 새 연결을 찾음
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
			return files[res]; //청크맵을 통해서 있는지 이미 확인했기 때문에 예외처리 필요하지 않음
	}
	public ChunkFileObj makeChunkfileObj(File file) throws Exception {
		if(file==null)
			return null;
		
		ChunkFileObj objchunk = new ChunkFileObj();
		byte[] chunk = new byte[10240];
		
		FileInputStream fis = new FileInputStream(file);
		BufferedInputStream bis = new BufferedInputStream(fis);
		int chunksize = bis.read(chunk);
		
		objchunk.setFilename(file.getName()); //객체에 이름 저장
		objchunk.setChunksize(chunksize);//객체에 크기 저장
		objchunk.setFiledata(chunk);//객체의 데이터 저장
		objchunk.setChunkcount(seeder.chunkcount);
		objchunk.setOriginName(seeder.originName);
		bis.close();
		fis.close();
		
		return objchunk;
	}
}
//클라이언트를 관리하는 쓰레드
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
			//세 쓰레드의 종료를 기다림
			c1.join();
			c2.join();
			c3.join();
			mergeFiles(Seeder.originName);
			System.out.println("File download completed!");
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	
	}
	//파일 합치기
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
//클라이언트 쓰레드
class cliThread extends Thread{
	Seeder seed; //leecher1의 변수 사용 위함
	OutputStream os = null;
	InputStream is = null;
	ObjectOutputStream oos = null;
	ObjectInputStream ois = null;
	int timeout = 10000; //타임아웃 10초
	static Set<String> requiremap = Collections.synchronizedSet(new HashSet<>());; //단순 연산용, 한 번 연결에 한 번만 필요
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
						Set<String> serChunkmap = (Set<String>) ois.readObject(); //1.서버로부터 청크맵 받기
						String requiring = filerequire(serChunkmap,seed.chunkmap);
						if(requiremap.size()==0) {
							oos.writeObject(0); //2.필요한 파일이 없을 경우 flag 0 보내주기
							break; //for문 탈출
						}
						//요청할 파일이 있는 경우
						oos.writeObject(1);
						oos.writeObject(requiring);//3. 무엇이 필요한지 string 보내기
						ChunkFileObj rcvObj =  (ChunkFileObj) ois.readObject();//4. 청크 수신
						if(rcvObj==null)
							break;
						Seeder.chunkcount = rcvObj.getChunkcount(); //전체 갯수 정보 저장
						Seeder.originName = rcvObj.getOriginName(); //원본 파일명 저장
						System.out.println("파일 수신<-- "+rcvObj.getFilename()+" "+soc);

						saveFile(rcvObj,seed.chunkmap);
						sleep(1000);
					}
					soc.close(); //모든 일 마쳤으므로, 소켓 통신 종료
					System.out.println(seed.chunkmap.size()+" / "+Seeder.chunkcount);
					if(seed.chunkmap.size()==Seeder.chunkcount)
						break;
				}
			}catch (ConnectException e1) { //연결 문제 발생 시, 다시 시작
				System.out.println("상태확인 및 다른 서버 찾기...");
			}catch(SocketException e2) {
				System.out.println("상태 확인 및 다른 서버 찾기...");
			}catch(TimeoutException e3) {
				System.out.println("상태 확인 및 다른 서버 찾기...");
			}
			catch(Exception e) {
				e.printStackTrace();
			}
			if((seed.chunkmap.size()==Seeder.chunkcount)&&(Seeder.chunkcount!=0)) {
				System.out.println("Download "+Thread.currentThread().getName()+" 종료");
				break; //while문 탈출하고 함수 종료
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
	//파일 저장할 때는 동시성을 피해야한다.
	public void saveFile(ChunkFileObj obj, Set<String> chunkmap) throws Exception {
		int chunksize = obj.getChunksize();
		String chunkname = obj.getFilename();
		byte[] chunkdata = obj.getFiledata();
		
		FileOutputStream fos = new FileOutputStream(new File(Seeder.chunkslocation,chunkname));
		BufferedOutputStream bos = new BufferedOutputStream(fos);
		bos.write(chunkdata, 0, chunksize);
		bos.close();
		//chunkmap.add(chunkname);//받은 오브젝트의 이름으로 chunkmap update
	
	}
}

