# HƯỚNG DẪN DÀNH CHO CODING AGENT (AGENT.md)

Tệp tin này lưu trữ toàn bộ bối cảnh dự án, tiêu chuẩn kỹ thuật, cấu trúc thư mục, và các nguyên tắc lập trình bắt buộc đối với mọi AI Coding Agent khi tham gia phát triển dự án **Mini Shopee**.

---

## 1. TỔNG QUAN DỰ ÁN (PROJECT OVERVIEW)
* **Tên dự án:** Đặt Hàng (Mini Shopee) - Hệ thống Backend dùng để kiểm thử tự động, kiểm thử bảo mật JWT và giao dịch nghiệp vụ.
* **Ngôn ngữ & Phiên bản:** Java 21
* **Build Tool:** Maven (`pom.xml`)
* **Khung công nghệ chính:**
  * Spring Boot 3.3.0
  * Spring Security & Spring Data JPA
  * PostgreSQL (Driver: `org.postgresql:postgresql`)
  * Validation (`spring-boot-starter-validation`)
  * Lombok & JJWT 0.11.5 (`io.jsonwebtoken:jjwt-api`, `-impl`, `-jackson`)

---

## 2. KIẾN TRÚC HỆ THỐNG & ĐƯỜNG DẪN THƯ MỤC
Dự án tuân thủ nghiêm ngặt kiến trúc 3 lớp tiêu chuẩn:
1. **Controller Layer (REST APIs):** Tiếp nhận yêu cầu, thực hiện validation `@Valid`, gọi Service.
2. **Service Layer (Nghiệp vụ):** Xử lý logic nghiệp vụ, bảo vệ bằng quản lý giao dịch `@Transactional`.
3. **Repository Layer (Spring Data JPA):** Truy vấn cơ sở dữ liệu.

### Sơ đồ cấu trúc thư mục:
* `/src/main/java/com/mini/shopee/`
  * `config/` - Cấu hình an ninh, JWT, Khởi tạo dữ liệu (`DataInitializer.java`).
  * `entity/` - Định nghĩa JPA Entity (`User.java`, `Product.java`, `Order.java`, `OrderItem.java`).
  * `dto/` - Các đối tượng truyền nhận thông tin (yêu cầu kiểm thử validation nghiêm ngặt đầu vào).
  * `exception/` - Quản lý lỗi tập trung (`GlobalExceptionHandler.java`).
  * `repository/` - Lớp JPA repository.
  * `service/` - Tầng nghiệp vụ chính.
  * `controller/` - Các API endpoints.
* `/src/test/java/com/mini/shopee/service/` - Tầng kiểm thử liên kết cơ sở dữ liệu.
* `/src/main/resources/static/docs/` - Thư mục lưu trữ tài liệu định dạng HTML phục vụ trực tiếp qua Web Server:
  * `style.css` - Phong cách giao diện tối đá phiến dùng chung.
  * `index.html` - Trang chủ cổng thông tin & Hướng dẫn kịch bản kiểm thử cho Tester.
  * `scalability_audit.html` - Báo cáo đánh giá chịu tải quy mô lớn (Database-level locks).
  * `concurrency_solution.html` - Giải pháp kiến trúc giảm chấn chịu tải cực hạn (Redis, Lua Script, Message Queue).

---

## 3. NGUYÊN TẮC LẬP TRÌNH BẮT BUỘC (CODING STANDARDS)

### 3.1. Tuyệt đối loại bỏ N+1 Query
Khi viết các câu lệnh truy vấn có liên kết Lazy Loading (ví dụ từ `Order` sang `OrderItem` và `Product`), bắt buộc sử dụng **Fetch Join** (`LEFT JOIN FETCH`) trong JPQL để gom truy vấn thành 1 câu lệnh duy nhất.
* *Tuyệt đối không sử dụng cách tải ngầm (lazy load) trong vòng lặp Java.*

### 3.2. Đảm bảo an toàn giao dịch Đặt hàng (Transaction & Concurrency Lock)
Khi thực hiện các giao dịch trừ kho hàng (`stock`) hoặc trừ tiền trong ví người dùng (`balance`):
* **Bắt buộc:** Đánh dấu phương thức bằng `@Transactional(rollbackFor = Exception.class)`.
* **Quy mô triệu sản phẩm/Triệu user (Tránh sập Database):** 
  * Áp dụng giải pháp **Kiểm tra & Khấu trừ kho nguyên tử trên RAM (In-Memory)** bằng **Redis + Lua Script**.
  * Chuyển các giao dịch ghi xuống PostgreSQL thành **xử lý bất đồng bộ (Asynchronous Processing)** thông qua **Message Queue** (RabbitMQ hoặc Kafka) làm bộ lọc giảm chấn bảo vệ database.
  * Ở tầng DB dự phòng, áp dụng **Khóa Bi quan (Pessimistic Locking)** (`LockModeType.PESSIMISTIC_WRITE`) khi cần đối soát đồng bộ.

### 3.3. Định dạng dữ liệu lỗi chuẩn (Global Exception Handling)
Mọi Exception ném ra từ Service (như `ResourceNotFoundException`, `BadRequestException`) hoặc lỗi Validation đầu vào bắt buộc phải được bắt tập trung tại `GlobalExceptionHandler.java` và trả về Client dưới định dạng cấu trúc JSON chuẩn:
```json
{
    "timestamp": "2026-06-01T21:00:00",
    "status": 400,
    "error": "Bad Request",
    "message": "Nội dung lỗi chi tiết",
    "details": { "tên_trường": "lỗi_validation_cụ_thể" }
}
```

---

## 4. TIÊU CHUẨN THIẾT KẾ TÀI LIỆU HTML/CSS (/docs/)
Để giữ giao diện tài liệu sạch sẽ, nhất quán và dễ bảo trì:
* **Tệp CSS dùng chung:** Luôn luôn liên kết tài liệu mới với tệp phong cách dùng chung `/docs/style.css` qua cú pháp: `<link rel="stylesheet" href="style.css">`.
* **Zero Inline Style:** Tuyệt đối nghiêm cấm viết thuộc tính `style="..."` trực tiếp trên các thẻ HTML trong tài liệu.
* **Tái sử dụng class tiện ích:**
  * Căn lề trên/dưới: Sử dụng `.mt-12`, `.mt-24`, `.mb-16`, `.mb-32`.
  * Trạng thái API Methods: Sử dụng `.method .method-get`, `.method-post`, `.method-delete`.
  * Hộp ghi chú: Sử dụng `.alert` (cảnh báo/vàng), `.alert-error` (lỗi/đỏ), `.alert-success` (thành công/xanh).
  * Chữ nhấn mạnh: Sử dụng `.text-highlight` (neon cyan), `.text-error` (neon red).

---

## 5. THÔNG TIN KHỞI CHẠY HẠ TẦNG (DOCKER)
* **Khởi chạy CSDL PostgreSQL local:**
  ```bash
  docker compose up postgres-db -d
  ```
* **Khởi chạy toàn bộ cụm ứng dụng (DB + App):**
  ```bash
  docker compose up --build -d
  ```
* **Tham số kết nối CSDL PostgreSQL mẫu:**
  * Host: `localhost` | Port: `5432`
  * Database: `shopee_mini`
  * Username: `sa_user` | Password: `sa_password`
