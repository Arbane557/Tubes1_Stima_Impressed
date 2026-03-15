# Tubes 1 Strategi Algoritma — Battlecode 2025
Repository ini berisi 1 bot utama dan 2 bot alternatif untuk menyelesaikan battlecode 2025 menggunakan algoritma greedy
## Penjelasan  Tiap Bot
- **General Impressive (main bot)**: Strategi pada alternatif robot ini mengedepankan penyelesaian objektif khusus untuk tiap tipe robot. Robot soldier memiliki objektif membangun tower, mopper menyerang robot lawan serta mendistribusikan cat, splasher mengecat sebanyak-banyaknya petak lawan ataupun petak kosong, serta tower membangun robot sebanyak-banyaknya dengan tipe robot sesuai dengan keadaan di sekitarnya. 
- **Minister Of Defense**: strategi pada alternatif robot ini fokus mengevaluasi pilihan berdasarkan dampaknya terhadap kemampuan defensif
- **Mr Capitalist**: Strategi bot ini berfokus pada objektif yang paling penting terlebih dahulu, yaitu membangun dan
melakukan upgrade tower agar chip dan paint cepat terkumpul.

## Requirement & Instalasi
- JDK 17 atau lebih baru (disarankan 17/21 sesuai Battlecode 2025).
- Gradle Wrapper sudah disertakan (`gradlew` / `gradlew.bat`), tidak perlu instal Gradle terpisah.
- OS: Windows/Linux/WSL. Untuk client AppImage di Linux/WSL perlu FUSE atau jalankan dengan `--appimage-extract`.

## How to play
1. Clone game engine
	```
	git clone https://github.com/Fariz36/STIMA-battle
	cd STIMA-battle
	```
2. Clone bot dari repo ini (di direktori sejajar dengan STIMA-battle)
    ```
	git clone https://github.com/Arbane557/Tubes1_Stima_Impressed.git
	```
3. Salin folder `src/` dari repo ini untuk menggantikan `src/` di template engine:
    - Hapus atau timpa `STIMA-battle/src` dengan `Tubes1_Stima_Impressed/src`.
    - Pastikan struktur akhir: `STIMA-battle/src/...` berisi kode bot ini.
4. Build & jalankan client:
	```
	./gradlew build
    ./gradlew run
	cd client
	```
4. Setelah build, jalankan aplikasi client yang dihasilkan.
5. Masuk ke opsi runner dan pilih direktori `STIMA-battle` sebagai root


## Author
| Nama                     | NIM       |
|--------------------------|-----------|
| Faiq Azzam Nafidz        | 13524003  |
| Raysha Erviandika Putra  | 13524050  |
| Zeki Amani               | 13524082  |
