# AIAdmin Hướng Dẫn Tiếng Việt

## Phần 1. Cách dùng và setup config

### 1. Cài plugin
1. Chép `AIAdmin.jar` vào thư mục `plugins` của server.
2. Chạy server một lần để AIAdmin tự tạo file cấu hình.
3. Tắt server rồi kiểm tra lại các file config trước khi bật các tính năng nâng cao.

### 2. Bộ config ngôn ngữ
AIAdmin có sẵn hai bộ config:
- `english/`
- `vietnamese/`

Mỗi bộ đều có các file:
- `config.yml`
- `aichat.yml`
- `option.yml`
- `rule.yml`
- `liteban.yml`
- `database.yml`
- `learning.yml`
- `setting_plugin.yml`
- thư mục `bot/`

Chỉ được bật một bộ config tại một thời điểm.
Trong hai file `config.yml` của hai thư mục, chỉ một bên được để `use-config: true`, bên còn lại phải là `false`.

Bạn cũng có thể đổi ngay trong game:
```text
/ai use config english
/ai use config vietnam
```

### 3. Đổi ngôn ngữ riêng cho từng người chơi
Người chơi có thể đổi ngôn ngữ AI riêng mà không ảnh hưởng toàn server:
```text
/ai lang english
/ai lang vietnam
```

### 4. Setup chat AI bằng OpenAI / Groq
Mở `config.yml` của bộ config đang dùng và kiểm tra các mục:
- `openai.enabled`
- `openai.api_key`
- `openai.endpoint`
- `openai.model`
- `openai.max_output_tokens`
- `openai.max_reply_chars`
- `openai.system_prompt`

Ví dụ cấu hình theo chuẩn OpenAI-compatible của Groq:
```yml
openai:
  enabled: true
  api_key: "YOUR_API_KEY"
  endpoint: "https://api.groq.com/openai/v1/responses"
  model: "llama-3.3-70b-versatile"
```

### 5. Setup tích hợp plugin khác
Dùng `setting_plugin.yml` để bật hoặc tắt các tích hợp.
Ví dụ:
- `liteban: true` để dùng lệnh LiteBans
- `placeholder: true` để đăng ký placeholder khi server có PlaceholderAPI
- `tab: true` để dùng tích hợp với TAB nếu cần

Nếu tích hợp bị tắt, AIAdmin sẽ tự dùng cơ chế nội bộ khi có hỗ trợ fallback.

### 6. Setup bot
Các file bot nằm tại:
- `bot/bot.yml`
- `bot/bot_body.yml`
- `bot/bot_rule.yml`

Bạn có thể chỉnh trong đó các phần như:
- follow / look / walk / jump
- bất tử
- hành vi quan sát của AI
- thân thể mannequin
- mốc kích hoạt và thời gian quan sát

### 7. Thứ tự test nên làm
Sau khi setup xong, nên test theo thứ tự:
1. `ai xin chào`
2. `/ai lang vietnam`
3. `/ai scan`
4. `/ai check <player>`
5. `/ai observe <player> on`
6. `/ai bot help`

## Phần 2. Cách dùng và ý nghĩa từng câu lệnh

### Lệnh cho member

#### `ai <nội dung>`
Nhắn trực tiếp với AIAdmin. Chỉ người gọi AI mới nhận được câu trả lời.

Ví dụ:
```text
ai luật server là gì
ai cách chơi ở đây như nào
```

#### `/ai lang english`
Chuyển AI cá nhân sang tiếng Anh.

#### `/ai lang vietnam`
Chuyển AI cá nhân sang tiếng Việt.

#### `/ai help`
Hiện phần hướng dẫn cơ bản cho người chơi thường.

### Lệnh cho admin

#### `/ai scan`
Quét toàn bộ người chơi ngay lập tức.

#### `/ai dashboard`
Mở GUI danh sách người chơi nghi vấn.

#### `/ai check <player> [gui|observe]`
Phân tích một người chơi trong chat.
- `gui`: mở GUI kiểm tra chi tiết.
- `observe`: bắt đầu quan sát ngay.

#### `/ai checkgui <player>`
Mở GUI kiểm tra chi tiết trực tiếp.

#### `/ai suspicion <player>`
Xem điểm nghi ngờ, mức cảnh báo và vị trí nghi ngờ gần nhất.

#### `/ai addsus <player> <amount>`
Cộng thêm điểm nghi ngờ thủ công.

#### `/ai flag <player> <type> [points] [details]`
Gắn cờ thủ công và cho AI bắt đầu quan sát.

Ví dụ:
```text
/ai flag Steve speed 8 di chuyển lạ
```

#### `/ai observe <player> <on/off>`
Bật hoặc tắt chế độ quan sát AI với một người chơi.

Ví dụ:
```text
/ai observe Steve on
/ai observe Steve off
```

#### `/ai kick <player> [reason]`
Kick một người chơi đang online.

#### `/ai termban <player> <reason> <time>`
Ban có thời hạn.
Có thể dùng các mốc như: `30m`, `12h`, `1d`, `3d`, `7d`.

Ví dụ:
```text
/ai termban Steve cheating 1d
/ai termban Steve 12h combat bất thường
```

#### `/ai ban <player> [reason]`
Ban người chơi qua LiteBans nếu đang bật, nếu không thì dùng fallback nội bộ.

#### `/ai thongbao <nội dung>`
Để AI viết lại câu thông báo và gửi ra toàn server.

Ví dụ:
```text
/ai thongbao server sẽ restart sau 5 phút
```

#### `/ai admode <on|off|status>`
Bật, tắt hoặc xem trạng thái relay công khai của admin.
Khi bật, Grox có thể đại diện admin nói chuyện công khai.

#### `/ai use config <english/vietnam>`
Đổi bộ config toàn server.

#### `/ai createbot <name>`
Tạo thân bot / mannequin.

#### `/ai choose bot <name>`
Chọn bot cần chỉnh sửa.

#### `/ai bot help`
Hiện toàn bộ hướng dẫn liên quan đến bot.

#### `/ai bot list`
Liệt kê tất cả bot hiện có và vị trí của chúng.

#### `/ai bot remove`
Xóa bot đang được chọn.

#### `/ai bot status`
Xem cấu hình hiện tại của bot đang chọn.

#### `/ai bot setup <key> <value>`
Đổi một thông số bot ngay trong game.

Ví dụ:
```text
/ai bot setup follow true
/ai bot setup look true
/ai bot setup invulnerable true
```

#### `/ai bot action add move <x1> <y1> <z1> <x2> <y2> <z2>`
Thêm một hành động di chuyển cho bot đang chọn.

### Ghi chú
- Có thể dùng gốc lệnh `/ai` hoặc `/aiadmin` nếu server map cả hai.
- Member không dùng được các lệnh quản trị và kiểm tra.
- Placeholder là tùy chọn, không cài PlaceholderAPI thì plugin vẫn chạy bình thường.
- LiteBans là tùy chọn, nếu không bật hoặc không cài thì AIAdmin sẽ dùng fallback nội bộ ở các chỗ có hỗ trợ.
