# Codeforces Quest - Minecraft Fabric Mod

Mod Fabric server-side cho Minecraft 1.21.1, tạo ra các nhiệm vụ lập trình thi đấu sử dụng bài toán từ Codeforces. Người chơi liên kết tài khoản Codeforces và thi đấu giải bài để nhận phần thưởng trong game.

## Yêu Cầu

- **Minecraft Server**: 1.21.1
- **Fabric Loader**: 0.16.0+
- **Fabric API**: 0.102.0+
- **Java**: 21+ (để build)

## Cài Đặt

### Cho Server Admin

1. Tải file `codeforces-minecraft-1.0.0.jar` từ thư mục `build/libs/`
2. Copy vào thư mục `mods/` của server Minecraft
3. Đảm bảo đã cài Fabric Loader và Fabric API
4. Khởi động server
5. Cấu hình tại `<world>/cfquest/config.json`

### Build Từ Source

```bash
# Yêu cầu Java 21+
JAVA_HOME=/path/to/java21 ./gradlew build

# Output: build/libs/codeforces-minecraft-1.0.0.jar
```

## Cấu Hình

File config tự động tạo tại `<world>/cfquest/config.json`:

```json
{
  "codeforces": {
    "apiKey": "",
    "apiSecret": "",
    "pollIntervalSeconds": 30
  },
  "quest": {
    "defaultTimeoutMinutes": 60,
    "penaltyMinutes": 20,
    "maxWinners": 3
  },
  "rewards": {
    "tiers": {
      "basic": { "minRating": 800, "items": ["minecraft:diamond", "minecraft:golden_apple"], "effect": null },
      "mid": { "minRating": 1300, "items": ["minecraft:diamond_block"], "effect": null },
      "high": { "minRating": 1700, "items": ["minecraft:netherite_ingot"], "effect": null },
      "elite": { "minRating": 2000, "items": ["minecraft:netherite_block"], "effect": "glowing" }
    },
    "placementMultipliers": { "1": 1.0, "2": 0.7, "3": 0.5 }
  },
  "schedule": {
    "enabled": false,
    "cron": "0 18 * * SAT",
    "problemPool": []
  }
}
```

### Codeforces API Key

Để truy cập private contest/mashup, bạn cần API key:

1. Đăng nhập Codeforces
2. Vào https://codeforces.com/settings/api
3. Tạo API key mới
4. Điền `apiKey` và `apiSecret` vào config

## Danh Sách Lệnh

### Lệnh Người Chơi

| Lệnh | Mô Tả |
|------|-------|
| `/cf link <handle>` | Liên kết tài khoản Minecraft với Codeforces |
| `/cf unlink` | Hủy liên kết tài khoản |
| `/cf status` | Xem trạng thái liên kết và thống kê cá nhân |
| `/cf quest info` | Xem thông tin quest đang diễn ra |
| `/cf leaderboard` | Xem bảng xếp hạng tổng |
| `/cf history` | Xem lịch sử 5 quest gần nhất |

### Lệnh Admin (Permission Level 2+)

| Lệnh | Mô Tả |
|------|-------|
| `/cf quest start <contestId> <index>` | Bắt đầu quest với bài từ contest |
| `/cf quest start <contestId> <index> <timeout>` | Bắt đầu quest với timeout tùy chỉnh (phút) |
| `/cf quest stop` | Hủy quest đang chạy |
| `/cf admin players` | Xem danh sách người chơi đã liên kết |
| `/cf admin forcepoll` | Kích hoạt poll Codeforces ngay lập tức |
| `/cf admin unlink <player>` | Hủy liên kết của người chơi khác |

## Hướng Dẫn Sử Dụng

### Cho Người Chơi

1. **Liên kết tài khoản**: `/cf link <handle_codeforces>`
2. **Đợi quest**: Admin sẽ thông báo khi có quest mới
3. **Giải bài**: Mở link bài, giải và submit trên Codeforces
4. **Nhận thưởng**: Mod tự động phát hiện và trao thưởng

### Cho Admin

1. **Chuẩn bị contest**: Tạo mashup/gym contest trên Codeforces với các bài muốn dùng
2. **Bắt đầu quest**: `/cf quest start <contestId> A` (A là index bài)
3. **Theo dõi**: Mod sẽ tự động poll và thông báo
4. **Kết thúc**: Quest tự kết thúc khi hết thời gian hoặc đủ người thắng

### Ví Dụ

```
# Bắt đầu quest với bài A từ contest 1234, timeout 45 phút
/cf quest start 1234 A 45

# Bắt đầu quest với gym contest
/cf quest start 104941 B

# Xem ai đã liên kết
/cf admin players
```

## Hệ Thống Tính Điểm (ICPC-style)

- **Thời gian giải**: Tính từ lúc quest bắt đầu đến lúc AC
- **Phạt sai**: Mỗi lần WA +20 phút (có thể cấu hình)
- **Tổng điểm**: `thời_gian_giải + (số_WA × 20 phút)`
- **Xếp hạng**: Điểm thấp hơn = hạng cao hơn

## Phần Thưởng

### Theo Độ Khó (Rating)

| Tier | Rating | Items Mặc Định |
|------|--------|----------------|
| Basic | 800-1299 | Diamond, Golden Apple |
| Mid | 1300-1699 | Diamond Block |
| High | 1700-1999 | Netherite Ingot |
| Elite | 2000+ | Netherite Block + Glowing |

### Theo Thứ Hạng

| Hạng | Multiplier |
|------|------------|
| 1 | 100% |
| 2 | 70% |
| 3 | 50% |

## Hỗ Trợ Crack Server

Mod hỗ trợ cả server online-mode và offline-mode (crack):
- **Online-mode**: Dùng UUID của Mojang
- **Offline-mode**: Dùng username (tự động phát hiện)

## Files Dữ Liệu

Tất cả dữ liệu lưu trong `<world>/cfquest/`:

| File | Mô Tả |
|------|-------|
| `config.json` | Cấu hình mod |
| `players.json` | Dữ liệu người chơi và liên kết CF |
| `history.json` | Lịch sử các quest đã hoàn thành |
| `active_quest.json` | Quest đang chạy (tự động xóa khi kết thúc) |

## Troubleshooting

### Quest không phát hiện submission

1. Kiểm tra người chơi đã `/cf link` chưa
2. Kiểm tra handle có đúng không
3. Thử `/cf admin forcepoll`
4. Xem log server để biết lỗi API

### Không truy cập được private contest

1. Đảm bảo đã cấu hình `apiKey` và `apiSecret`
2. Kiểm tra API key còn hiệu lực
3. Đảm bảo bạn có quyền truy cập contest đó

### Lỗi khi build

- Đảm bảo dùng Java 21+
- Xóa thư mục `.gradle` và `build`, thử lại

## CI/CD với GitHub Actions

### Tự Động Build

Mỗi khi push code lên `main`/`master`/`develop` hoặc tạo PR, GitHub Actions sẽ tự động build và upload artifact.

### Tạo Release

```bash
# Cập nhật version trong gradle.properties
# mod_version=1.1.0

# Commit và tag
git add .
git commit -m "Release v1.1.0"
git tag v1.1.0
git push origin main --tags
```

GitHub Actions sẽ tự động:
1. Build JAR
2. Tạo GitHub Release với JAR đính kèm

### Publish lên Modrinth/CurseForge (Optional)

1. Vào **Settings > Secrets and variables > Actions**

2. Thêm **Repository variables**:
   - `PUBLISH_MODRINTH`: `true` (để bật)
   - `PUBLISH_CURSEFORGE`: `true` (để bật)
   - `MODRINTH_PROJECT_ID`: ID project trên Modrinth
   - `CURSEFORGE_PROJECT_ID`: ID project trên CurseForge

3. Thêm **Repository secrets**:
   - `MODRINTH_TOKEN`: API token từ https://modrinth.com/settings/pats
   - `CURSEFORGE_TOKEN`: API token từ https://curseforge.com/account/api-tokens

## License

MIT License
