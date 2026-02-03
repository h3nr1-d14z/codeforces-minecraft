# Roadmap - Codeforces Quest Mod

## Đã Hoàn Thành (v1.0.0)

- [x] Cấu trúc project Fabric mod
- [x] Codeforces API client với rate limiting
- [x] Hệ thống liên kết tài khoản (hỗ trợ crack players)
- [x] Quest system với timeout
- [x] ICPC-style penalty tracking
- [x] Phần thưởng items theo rating tier
- [x] Status effects cho winners
- [x] Bảng xếp hạng và lịch sử
- [x] Lệnh admin đầy đủ
- [x] Thông báo tiếng Việt
- [x] Lưu trữ dữ liệu JSON

---

## Phase 2: Cải Thiện UX (v1.1.0)

### Thông Báo Nâng Cao
- [ ] Boss bar hiển thị thời gian còn lại của quest
- [ ] Action bar thông báo khi có người giải
- [ ] Sound effects đa dạng hơn
- [ ] Particle effects cho winners

### GUI
- [ ] Menu GUI để xem quest info (thay vì chat)
- [ ] Leaderboard GUI với pagination
- [ ] Personal stats GUI

### Quality of Life
- [ ] Auto-complete cho handle khi `/cf link`
- [ ] Confirm dialog trước khi unlink
- [ ] `/cf help` với hướng dẫn chi tiết

---

## Phase 3: Tính Năng Mở Rộng (v1.2.0)

### Multiple Problems per Quest
- [ ] Hỗ trợ quest với nhiều bài (như contest thật)
- [ ] Tính điểm tổng hợp
- [ ] Partial scoring

### Team Mode
- [ ] Tạo team trong game
- [ ] Team leaderboard
- [ ] Team rewards

### Scheduled Quests
- [ ] Cải thiện cron parser
- [ ] Random problem selection từ rating range
- [ ] Announcement trước khi quest bắt đầu (5 phút)
- [ ] Recurring weekly/daily quests

---

## Phase 4: Tích Hợp Sâu (v1.3.0)

### Discord Integration
- [ ] Webhook thông báo quest bắt đầu/kết thúc
- [ ] Leaderboard embed
- [ ] Link Discord account với MC account

### Database Backend
- [ ] Hỗ trợ SQLite/MySQL thay vì JSON
- [ ] Query lịch sử hiệu quả hơn
- [ ] Backup tự động

### Web Dashboard
- [ ] API endpoint để query stats
- [ ] Web UI để quản lý quest
- [ ] Public leaderboard page

---

## Phase 5: Competitive Features (v2.0.0)

### Rating System
- [ ] Internal rating cho người chơi (như CF rating)
- [ ] Rating gain/loss sau mỗi quest
- [ ] Matchmaking dựa trên rating

### Achievements
- [ ] Achievement system (First AC, Speed Demon, etc.)
- [ ] Achievement rewards
- [ ] Achievement display

### Seasons
- [ ] Season-based leaderboard reset
- [ ] Season rewards
- [ ] Historical season data

### Tournament Mode
- [ ] Bracket-style tournaments
- [ ] Elimination rounds
- [ ] Grand finals với special rewards

---

## Ideas Backlog (Chưa Lên Lịch)

### Gameplay
- [ ] Custom problem sets (không cần CF)
- [ ] Practice mode (không tính điểm)
- [ ] Hint system (đổi điểm lấy hint)
- [ ] Problem voting (người chơi vote bài tiếp)

### Rewards
- [ ] Custom items với NBT đặc biệt
- [ ] Cosmetic rewards (particles, titles)
- [ ] In-game currency system
- [ ] Shop để đổi currency

### Social
- [ ] Challenge người chơi khác 1v1
- [ ] Spectator mode
- [ ] Replay submission history

### Integration
- [ ] AtCoder support
- [ ] LeetCode support
- [ ] VNOJ/CSES support
- [ ] Custom judge server

---

## Contributing

Nếu bạn muốn đóng góp:

1. Fork repository
2. Tạo branch cho feature: `git checkout -b feature/ten-feature`
3. Commit changes: `git commit -m "Add: mô tả feature"`
4. Push và tạo Pull Request

### Priority Labels
- `P0`: Critical - cần làm ngay
- `P1`: High - nên làm sớm
- `P2`: Medium - làm khi có thời gian
- `P3`: Low - nice to have

---

## Changelog

### v1.0.0 (2024-02-04)
- Initial release
- Core quest system
- CF API integration
- Reward system
- Admin commands
