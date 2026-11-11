# NotePad

Ứng dụng ghi chú Android viết bằng Kotlin, Material Design 3, hỗ trợ viết tay bằng bút cảm ứng.

## Màn hình

- **Splash**: SplashScreen API + màn hình chào có logo, phiên bản
- **Main**: danh sách ghi chú dạng lưới, tìm kiếm, nhấn giữ để xóa, FAB tạo mới (nhấn giữ FAB để mở thẳng chế độ vẽ)
- **Editor**: chế độ Văn bản / Vẽ tay
- **About**: thông tin ứng dụng, tính năng, nhà phát triển
- **Terms**: điều khoản sử dụng
- **Privacy**: chính sách quyền riêng tư

## Hỗ trợ bút cảm ứng (TouchPen / S Pen / USI)

| Tính năng | Cách hoạt động |
|---|---|
| Lực nhấn | Độ dày nét thay đổi theo `MotionEvent.pressure` |
| Đầu tẩy | `TOOL_TYPE_ERASER` tự chuyển sang tẩy |
| Nút bên thân bút | Giữ `BUTTON_STYLUS_PRIMARY/SECONDARY` để tẩy |
| Nét mượt, độ trễ thấp | Dùng historical points + `requestUnbufferedDispatch` |
| Hover | Hiển thị vòng tròn kích thước nét khi rê bút |
| Chống chạm lòng bàn tay | Menu "Chỉ nhận bút cảm ứng" bỏ qua ngón tay, xử lý `FLAG_CANCELED` |
| Viết tay vào ô văn bản | Stylus handwriting của Android 14+ (cần bàn phím hỗ trợ, ví dụ Gboard) |

Bút điện dung thụ động (đầu cao su) hệ thống nhận là ngón tay, vẫn vẽ được khi tắt chế độ "Chỉ nhận bút".

## Công nghệ

- Kotlin 2.0, AGP 8.7, Gradle 8.11, JDK 17
- minSdk 26, targetSdk 35, edge-to-edge
- Material Components 1.12 (Material 3, Dynamic Color)
- ViewBinding, Coroutines
- Lưu trữ cục bộ: JSON + PNG trong `filesDir`, không cần quyền Internet

## Build

```bash
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/`

## GitHub Actions

Workflow `.github/workflows/android.yml`:

- Push / PR vào `main`: build debug + release, upload APK làm artifact
- Push tag `v*` (ví dụ `v1.0.0`): build và tạo GitHub Release kèm APK

```bash
git tag v1.0.0
git push origin v1.0.0
```

### Ký APK release (tuỳ chọn)

Không cấu hình secrets thì release APK là bản unsigned. Để ký, thêm các secret trong **Settings > Secrets and variables > Actions**:

| Secret | Giá trị |
|---|---|
| `KEYSTORE_BASE64` | `base64 -w0 release.jks` |
| `SIGNING_STORE_PASSWORD` | Mật khẩu keystore |
| `SIGNING_KEY_ALIAS` | Alias của key |
| `SIGNING_KEY_PASSWORD` | Mật khẩu key |

Tạo keystore:

```bash
keytool -genkeypair -v -keystore release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias notepad
```

## License

MIT © 2026 Nguyễn Quốc Anh
