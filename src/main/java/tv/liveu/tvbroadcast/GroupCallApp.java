/*
 * (C) Copyright 2016 LiveU (http://liveu.tv/)
 *
 * TV Broadcast Application
 * 
 * Filename	: GroupCallApp.java
 * Purpose	: SpringBootApplication runner    
 * Author	: Sergey K
 * Created	: 10/08/2016
 */


package tv.liveu.tvbroadcast;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import redsoft.dsagent.*;

import tv.liveu.picturestorage.StorageService;
import tv.liveu.picturestorage.StorageFileNotFoundException;
import tv.liveu.picturestorage.StorageProperties;
import tv.liveu.picturestorage.PictureStorageService;
import tv.liveu.picturestorage.StorageUploadResponse;


@SpringBootApplication
@EnableWebSocket
@Controller
@EnableConfigurationProperties(StorageProperties.class)
public class GroupCallApp implements WebSocketConfigurer {

  @Autowired
    
  @Bean
  public UserRegistry registry() {
    return new UserRegistry();
  }

  @Bean
//  @DependsOn("kurento")
  public RoomManager roomManager() {
	  RoomManager r = null;
	  try {
		  r = new RoomManager();
	  } catch (Exception e) {
		  e.printStackTrace(System.err);
		  //throw e;
	  }
	  return r;
  }

  @Bean
  public CallHandler groupCallHandler() {
    return new CallHandler();
  }

//  @Bean(name="kurento")
//  public KurentoClient kurentoClient() {
//    return KurentoClient.create();
//  }

  public static void main(String[] args) throws Exception {
	Settings.init();

	if (Settings.isPdsActive(PDs.PDS_STD))
		PDs.newStd();
	if (Settings.isPdsActive(PDs.PDS_ERR))
		PDs.newErr();
	if (Settings.isPdsActive(PDs.PDS_FILE))
		PDs.newFile(Settings.DS_FILEPATH);
	if (Settings.isPdsActive(PDs.PDS_UDP))
		PDs.newUdp(Settings.DS_UDPSRV);

	Ds.Init();
	Ds.dsSys.mainInfo("TVB-MAIN", "TV-Broadcast", "0.1");

	//Settings.print(System.err);
	Settings.print2Ds();

	SpringApplication.run(GroupCallApp.class, args);
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry.addHandler(groupCallHandler(), "/groupcall");
  }
  
  @GetMapping("/test")
  public ResponseEntity<String> testting(@RequestParam(value = "name", defaultValue = "World") String name,
          @RequestBody(required = false) String body) {
    System.err.println("body: " + body);
    //return new String("testing string conternt");
    return ResponseEntity.ok().body("test " + name);
  }
  
  
    private final StorageService storageService;


    public GroupCallApp() {
        System.err.println("GroupCallApp initalization");
        storageService = new PictureStorageService(new StorageProperties());
        //storageService.deleteAll();
        storageService.init();
    }
    
    @GetMapping("/")
    public ResponseEntity<String> index() {
        System.err.println("Requesting ROOT");
        return ResponseEntity.ok().body("<head><meta http-equiv=\"refresh\" content=\"1;URL=index3.html\" /></head>");
    }
    
    @GetMapping("/pictures")
    public String listUploadedFiles(Model model) throws IOException {

        model.addAttribute("files", storageService
                .loadAll()
                .map(new Function<Path, String>() {
                    @Override
                    public String apply(Path path) {
                        return MvcUriComponentsBuilder
                                .fromMethodName(GroupCallApp.class, "serveFile", path.getFileName().toString())
                                .build().toString();
                    }
                })
                .collect(Collectors.toList()));

        return "uploadForm";
    }
    
    @GetMapping("/files/{filename:.+}")
    @ResponseBody
    public ResponseEntity<Resource> serveFile(@PathVariable String filename) {

                System.err.println("/files");
        
        Resource file = storageService.loadAsResource(filename);
        return ResponseEntity
                .ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\""+file.getFilename()+"\"")
                .body(file);
    }
    
    
    @PostMapping("/upload")
    public ResponseEntity<StorageUploadResponse> handleFileUpload(@RequestParam("file") MultipartFile file,
                                   RedirectAttributes redirectAttributes) {
        storageService.store(file);
/*
        redirectAttributes.addFlashAttribute("message",
                "You successfully uploaded " + file.getOriginalFilename() + "!");
        return "redirect:/pictures";
*/
        
        return ResponseEntity
                .ok()
                .body(new StorageUploadResponse(1, file.getOriginalFilename()));
    }


    @ExceptionHandler(StorageFileNotFoundException.class)
    public ResponseEntity handleStorageFileNotFound(StorageFileNotFoundException exc) {
        return ResponseEntity.notFound().build();
    }

}
