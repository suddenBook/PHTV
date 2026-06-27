# PornHub — reverse-engineering notes (data layer)

> Captured live via headless Chrome DevTools on 2026-06-27. There is **no official public API**;
> everything below is derived from the public website's HTML/JS and network traffic. The site changes
> often — treat selectors/params as "verify on failure", and centralise them so they're easy to patch.

## TL;DR architecture

| Concern | Mechanism | Source |
|---|---|---|
| Listings (home/category/search) | **Server-rendered HTML** → scrape with Jsoup | `li.pcVideoListItem` cards |
| Stream URLs | **`flashvars_<id>` JSON** embedded in the watch page | per-quality HLS `master.m3u8` |
| Categories | Fixed taxonomy, browse via `/video?c=<id>` | 96 ids mapped below |
| Playback | HLS (`.m3u8`) — site itself uses `hls.js` | Media3 `media3-exoplayer-hls` plays it directly |

No login is required for browsing or playback of free videos (headless session with no cookies worked).

---

## 1. Listing / browse pages (scrape)

All listing pages are HTML. Each video card:

- Container: `li.pcVideoListItem` (≈58 per page on home), thumbnail wrapper `.phimage`
- Watch link: `a[href*="view_video.php?viewkey="]` → **viewkey** is the stable video id
- Title: `.title a` (also used as the link text)
- Plus (in card markup): duration overlay, view count, rating, preview-on-focus webm, HD badge, uploader

URL patterns:

| Page | URL |
|---|---|
| Home | `https://www.pornhub.com/` |
| All videos | `https://www.pornhub.com/video` |
| Category | `https://www.pornhub.com/video?c=<id>` |
| Search | `https://www.pornhub.com/video/search?search=<q>` |
| Pornstars | `https://www.pornhub.com/pornstars` |
| Watch | `https://www.pornhub.com/view_video.php?viewkey=<viewkey>` |

Query params (observed on listing pages):

- Sort `o=`: `ht` Hottest · `mv` Most Viewed · `tr` Top Rated · `cm` Newest (recently added)
- Pagination: `page=<n>`
- Production filter: `p=professional` | `p=homemade`
- Time window (standard, verify): `t=` `w`/`m`/`y`/`a` (week/month/year/all-time)

Example: top-rated page 2 of MILF this month → `/video?c=29&o=tr&t=m&page=2`

---

## 2. Watch page → stream extraction (the important bit)

The watch page embeds a global JS object **`flashvars_<videoId>`** (videoId is numeric, distinct from the
viewkey). Extract the JSON assigned to it from the page `<script>` (regex `var flashvars_\d+ = (\{.*?\});`)
and parse. Key fields:

- `mediaDefinitions[]` — the stream list (see below)
- `video_title`, `image_url` (poster), `thumbs` (storyboard/preview sprite), `video_duration` (seconds)
- `nextVideo`, `related_url`, `mostviewed_url`, `toprated_url`, `autoplay`, `isVR`, `isHD`

### `mediaDefinitions`

Two kinds of entries appear together:

1. **Direct per-quality HLS** (preferred — play these):
   ```json
   { "format": "hls", "quality": "1080",
     "videoUrl": "https://hv-h.phncdn.com/hls/.../1080P_4000K_<id>.mp4/master.m3u8?h=<sig>&e=<expiry>&f=1" }
   ```
   One entry per quality (observed: 1080 / 720 / 480 / 240). `videoUrl` is a directly-playable HLS master.

2. **`get_media` resolver** (fallback / canonical):
   ```json
   { "format": "mp4", "quality": "",
     "videoUrl": "https://www.pornhub.com/video/get_media?s=<base64>&v=<viewkey>&e=0&t=p" }
   ```
   GET this URL (with `x-requested-with: XMLHttpRequest`) → JSON array of `{quality, videoUrl, format}`.

### ⚠️ Signed + expiring URLs

The `h=` is an HMAC signature and `e=` is a Unix expiry (~**1 hour** after page load, and typically
IP-bound). **Do not cache stream URLs.** Resolve `flashvars` fresh immediately before playback.

### Playback headers

Segments are served from `*.phncdn.com`. Send a realistic desktop `User-Agent` and
`Referer: https://www.pornhub.com/` on the Media3 HTTP data source. The signature handles auth; no cookies
needed for the CDN. (The website plays via `hls.js`, confirming standard HLS.)

---

## 3. Categories (full taxonomy)

Browse: `https://www.pornhub.com/video?c=<id>`. The `/categories` page is the source of truth and can be
scraped live; the complete id→name map observed on 2026-06-27 (96 entries) — use as a static fallback:

```
1 Asian · 2 Orgy · 3 Amateur · 4 Big Ass · 6 BBW · 7 Big Dick · 8 Big Tits · 9 Blonde · 10 Bondage
11 Brunette · 12 Celebrity · 13 Blowjob · 14 Bukkake · 15 Creampie · 16 Cumshot · 17 Ebony · 18 Fetish
19 Fisting · 20 Handjob · 21 Hardcore · 22 Masturbation · 23 Toys · 24 Public · 25 Interracial · 26 Latina
27 Lesbian · 28 Mature · 29 MILF · 31 Reality · 32 Funny · 33 Striptease · 35 Anal · 41 POV · 42 Red Head
43 Vintage · 53 Party · 55 Euro · 57 Compilation · 59 Small Tits · 61 Webcam · 65 Threesome · 67 Rough Sex
69 Squirt · 72 Double Penetration · 76 Bisexual Male · 78 Massage · 80 Gangbang · 81 Role Play · 86 Cartoon
88 School (18+) · 89 Babysitter (18+) · 90 Casting · 91 Smoking · 92 Solo Male · 93 Feet · 94 French
95 German · 96 British · 97 Italian · 98 Arab · 99 Russian · 100 Czech · 101 Indian · 102 Brazilian
103 Korean · 105 60FPS · 111 Japanese · 115 Exclusive · 121 Music · 131 Pussy Licking · 138 Verified Amateurs
139 Verified Models · 141 Behind The Scenes · 181 Old/Young (18+) · 201 Parody · 211 Pissing · 241 Cosplay
242 Cuckold · 444 Step Fantasy · 482 Verified Couples · 492 Solo Female · 502 Female Orgasm · 512 Muscular Men
522 Romantic · 532 Scissoring · 542 Strap On · 562 Tattooed Women · 572 Trans With Girl · 592 Fingering
612 360° · 712 Uncensored · 722 Uncensored · 732 Audio Impaired · 761 FFM · 881 Gaming · 891 Podcast
```

Some categories also expose vanity slugs, e.g. `/categories/teen` (18-25), `/categories/hentai`.

Menu fragment endpoint (alternative nav source): `GET /front/menu_all_cached?segment=straight&token=<t>`
returns an **HTML fragment** (not JSON); requires the `token` parsed from the page plus
`x-requested-with: XMLHttpRequest` and session cookies.

---

## 4. Session / headers / anti-bot

- Set cookie `cookieConsent=1` (and `platform=pc`) to skip the consent modal.
- Use a realistic desktop `User-Agent`. Headless Chrome with the default desktop UA loaded home, category,
  and watch pages with HTTP 200 and no age/consent wall blocking content.
- Raw OkHttp scraping may face more friction than a real browser (Cloudflare / "risk control"). If we see
  403s/challenges, mirror browser headers (UA, `Accept-Language`, `Referer`) and persist cookies across
  requests. (blbl keeps a dedicated "risk" module for the equivalent on Bilibili.)
- `segment=straight` selects orientation; gay/trans have separate segments/subdomains.

---

## 5. Client implications

- Two OkHttp clients: **api/html** (browser-like headers + cookie jar) and **cdn** (UA + Referer, no cookies).
- Repository: `listHome/listCategory/search(page)` → parse cards → `Video` models; `resolveStream(viewkey)`
  → fetch watch page → parse `flashvars` → `List<StreamSource(quality, m3u8Url)>`.
- Resolve streams lazily at playback; surface a quality picker from `mediaDefinitions`.
- Media3: `media3-exoplayer-hls` + `DefaultHttpDataSource.Factory` with the Referer/UA default headers.
