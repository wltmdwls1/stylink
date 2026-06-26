# 파일 업로드

> 스프링 MVC 2편 | MVC2

---

## 전체 흐름 한눈에 보기

```
multipart/form-data (왜 필요한가)
  application/x-www-form-urlencoded: 문자만 전송 가능 → 파일(바이너리) 전송 불가
  multipart/form-data: 각 Part로 분리 → 문자 + 바이너리 동시 전송

서블릿 파일 업로드
  request.getParts() → Collection<Part>
  part.getSubmittedFileName() / part.getInputStream() / part.write(path)
  spring.servlet.multipart.enabled=true (기본값)

스프링 파일 업로드 (권장)
  @RequestParam MultipartFile file
  file.getOriginalFilename() / file.transferTo(new File(path))

파일 업로드/다운로드 전체 예제
  UploadFile: uploadFileName(고객 파일명) + storeFileName(UUID.확장자)
  FileStore: 서버 저장 책임 — UUID로 충돌 방지
  이미지 조회: @ResponseBody + UrlResource
  첨부파일 다운로드: Content-Disposition: attachment; filename="인코딩파일명"
```

---

## multipart/form-data 소개

### 폼 전송 방식 비교

| 방식 | Content-Type | 특징 |
|------|-------------|------|
| `application/x-www-form-urlencoded` | 기본값 | 문자만 전송. `username=kim&age=20` 형식. 파일(바이너리) 불가. |
| `multipart/form-data` | `enctype="multipart/form-data"` 지정 | 문자 + 바이너리 동시 전송. 각 항목을 Part로 분리. |

### HTTP 메시지 구조

```
Content-Type: multipart/form-data; boundary=------xxxx

------xxxx
Content-Disposition: form-data; name="itemName"

Spring
------xxxx
Content-Disposition: form-data; name="file"; filename="test.data"
Content-Type: application/octet-stream

(바이너리 데이터)
------xxxx--
```

---

## 서블릿 파일 업로드

### 멀티파트 설정 (application.properties)

```properties
spring.servlet.multipart.enabled=true       # 기본값 true
spring.servlet.multipart.max-file-size=1MB  # 파일 하나 최대 (기본 1MB)
spring.servlet.multipart.max-request-size=10MB # 요청 전체 최대 (기본 10MB)

# HTTP 요청 메시지 확인용 (개발 시)
logging.level.org.apache.coyote.http11=trace
```

> `enabled=true`이면 `HttpServletRequest`가 `StandardMultipartHttpServletRequest`로 변환됨.  
> `enabled=false`이면 `request.getParameter()`, `request.getParts()` 모두 비어있음.

### Part API

```java
@PostMapping("/upload")
public String saveFileV1(HttpServletRequest request) throws ServletException, IOException {
    String itemName = request.getParameter("itemName");  // 일반 폼 필드도 그대로 조회
    Collection<Part> parts = request.getParts();

    for (Part part : parts) {
        log.info("name={}", part.getName());
        log.info("submittedFileName={}", part.getSubmittedFileName()); // 파일명 (파일 part만 존재)
        log.info("size={}", part.getSize());

        // 데이터 읽기
        InputStream inputStream = part.getInputStream();
        String body = StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);

        // 파일 저장
        if (StringUtils.hasText(part.getSubmittedFileName())) {
            String fullPath = fileDir + part.getSubmittedFileName();
            part.write(fullPath);
        }
    }
    return "upload-form";
}
```

### Part 주요 메서드

| 메서드 | 설명 |
|--------|------|
| `part.getName()` | 폼 필드 name |
| `part.getSubmittedFileName()` | 클라이언트가 전달한 파일명 (파일 아닌 경우 null) |
| `part.getSize()` | Part 바디 크기 |
| `part.getInputStream()` | Part 데이터 스트림 |
| `part.write(path)` | 파일로 저장 |

---

## 스프링 파일 업로드 — MultipartFile

```java
@PostMapping("/upload")
public String saveFile(@RequestParam String itemName,
                       @RequestParam MultipartFile file,
                       HttpServletRequest request) throws IOException {
    log.info("itemName={}", itemName);
    log.info("multipartFile={}", file);

    if (!file.isEmpty()) {
        String fullPath = fileDir + file.getOriginalFilename();
        file.transferTo(new File(fullPath));
    }
    return "upload-form";
}
```

> `@ModelAttribute`에서도 `MultipartFile` 필드를 동일하게 사용할 수 있다.

### MultipartFile 주요 메서드

| 메서드 | 설명 |
|--------|------|
| `file.getOriginalFilename()` | 업로드 원본 파일명 |
| `file.isEmpty()` | 파일이 비어있는지 확인 |
| `file.transferTo(File dest)` | 파일 저장 |
| `file.getInputStream()` | 바이트 스트림 |
| `file.getSize()` | 파일 크기 |

---

## 파일 업로드/다운로드 전체 예제

### 핵심 설계: 파일명 분리

```java
@Data
public class UploadFile {
    private String uploadFileName;  // 고객이 업로드한 원본 파일명 (ex: 사진.png)
    private String storeFileName;   // 서버에서 관리하는 UUID 파일명 (ex: uuid.png)
}
```

> 동일한 파일명 충돌 방지 — 서버 저장 시 UUID 사용, 다운로드 시 원본 파일명 복원.

### FileStore — 파일 저장 처리

```java
@Component
public class FileStore {
    @Value("${file.dir}")
    private String fileDir;

    public String getFullPath(String filename) {
        return fileDir + filename;
    }

    public UploadFile storeFile(MultipartFile multipartFile) throws IOException {
        if (multipartFile.isEmpty()) return null;
        String originalFilename = multipartFile.getOriginalFilename();
        String storeFileName = createStoreFileName(originalFilename); // UUID.확장자
        multipartFile.transferTo(new File(getFullPath(storeFileName)));
        return new UploadFile(originalFilename, storeFileName);
    }

    public List<UploadFile> storeFiles(List<MultipartFile> multipartFiles) throws IOException {
        List<UploadFile> result = new ArrayList<>();
        for (MultipartFile multipartFile : multipartFiles) {
            if (!multipartFile.isEmpty()) {
                result.add(storeFile(multipartFile));
            }
        }
        return result;
    }

    private String createStoreFileName(String originalFilename) {
        String ext = extractExt(originalFilename);
        return UUID.randomUUID().toString() + "." + ext;
    }

    private String extractExt(String originalFilename) {
        int pos = originalFilename.lastIndexOf(".");
        return originalFilename.substring(pos + 1);
    }
}
```

### ItemForm — @ModelAttribute에서 MultipartFile 사용

```java
@Data
public class ItemForm {
    private Long itemId;
    private String itemName;
    private MultipartFile attachFile;           // 단일 첨부파일
    private List<MultipartFile> imageFiles;     // 다중 이미지
}

// 다중 업로드 HTML: multiple="multiple" 속성
// <input type="file" multiple="multiple" name="imageFiles">
```

### ItemController — 업로드/다운로드

```java
// 상품 저장
@PostMapping("/items/new")
public String saveItem(@ModelAttribute ItemForm form, RedirectAttributes redirectAttributes)
        throws IOException {
    UploadFile attachFile = fileStore.storeFile(form.getAttachFile());
    List<UploadFile> storeImageFiles = fileStore.storeFiles(form.getImageFiles());

    Item item = new Item();
    item.setItemName(form.getItemName());
    item.setAttachFile(attachFile);
    item.setImageFiles(storeImageFiles);
    itemRepository.save(item);

    redirectAttributes.addAttribute("itemId", item.getId());
    return "redirect:/items/{itemId}";
}

// 이미지 조회 (브라우저에서 <img> 태그로 직접 요청)
@ResponseBody
@GetMapping("/images/{filename}")
public Resource downloadImage(@PathVariable String filename) throws MalformedURLException {
    return new UrlResource("file:" + fileStore.getFullPath(filename));
}

// 첨부파일 다운로드
@GetMapping("/attach/{itemId}")
public ResponseEntity<Resource> downloadAttach(@PathVariable Long itemId) throws MalformedURLException {
    Item item = itemRepository.findById(itemId);
    String storeFileName = item.getAttachFile().getStoreFileName();
    String uploadFileName = item.getAttachFile().getUploadFileName();

    UrlResource resource = new UrlResource("file:" + fileStore.getFullPath(storeFileName));

    // 한글 파일명 인코딩 (Content-Disposition에 사용)
    String encodedUploadFileName = UriUtils.encode(uploadFileName, StandardCharsets.UTF_8);
    String contentDisposition = "attachment; filename=\"" + encodedUploadFileName + "\"";

    return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
            .body(resource);
}
```

### 조회 뷰 핵심 (Thymeleaf)

```html
<!-- 첨부파일 링크 (고객 파일명 표시, 서버 ID로 다운로드) -->
<a th:if="${item.attachFile}" 
   th:href="|/attach/${item.id}|" 
   th:text="${item.getAttachFile().getUploadFileName()}"/>

<!-- 이미지 다중 출력 (서버 저장명으로 조회) -->
<img th:each="imageFile : ${item.imageFiles}" 
     th:src="|/images/${imageFile.getStoreFileName()}|" 
     width="300" height="300"/>
```

---

## 면접 Q&A

**Q: 파일 업로드 시 multipart/form-data를 사용하는 이유는?**
A: 일반적인 `application/x-www-form-urlencoded`는 폼 데이터를 문자로만 전송한다. 파일은 바이너리 데이터이므로 이 방식으로는 전송할 수 없다. `multipart/form-data`는 각 항목을 Part로 분리해서 문자 데이터와 바이너리 데이터를 한 번에 전송할 수 있다.

**Q: 업로드 파일명을 서버에서 UUID로 변경해서 저장하는 이유는?**
A: 다른 사용자가 동일한 파일명으로 업로드하면 기존 파일이 덮어씌워질 수 있다. UUID로 생성한 고유한 이름으로 저장하면 파일명 충돌이 없다. 고객이 원본 파일명을 필요로 하는 경우(다운로드 시)를 위해 DB에 `uploadFileName`(원본)과 `storeFileName`(UUID)을 함께 저장한다.

**Q: 파일 다운로드 시 Content-Disposition 헤더를 설정하는 이유는?**
A: 브라우저가 파일을 바로 열지 않고 다운로드하도록 유도하기 위해 `Content-Disposition: attachment; filename="파일명"` 헤더를 설정한다. 서버 내부 저장명(UUID)이 아닌 고객 원본 파일명으로 다운로드되도록 한다. 한글 파일명이 포함될 경우 `UriUtils.encode()`로 URL 인코딩을 해야 깨지지 않는다.
