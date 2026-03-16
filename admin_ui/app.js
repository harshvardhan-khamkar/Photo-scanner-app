/* ── Wedding Memory — Admin Dashboard ────────────────────────── */

// ── Theme Management ───────────────────────────────────────────
function initTheme() {
    const isDark = localStorage.getItem('adminTheme') === 'dark';
    if (isDark) {
        document.body.classList.add('dark-theme');
    }
    updateThemeIcon();
}

function toggleTheme() {
    const isDark = document.body.classList.toggle('dark-theme');
    localStorage.setItem('adminTheme', isDark ? 'dark' : 'light');
    updateThemeIcon();
}

function updateThemeIcon() {
    const isDark = document.body.classList.contains('dark-theme');
    const btn = document.getElementById('themeToggleBtn');
    if (btn) {
        btn.innerHTML = `<i data-lucide="${isDark ? 'sun' : 'moon'}" class="icon-sm"></i>`;
        if (window.lucide) window.lucide.createIcons();
    }
}
document.addEventListener('DOMContentLoaded', initTheme);

// ── API config ──────────────────────────────────────────────────
// If running directly from file index.html, fallback to localhost:8000
const API = window.location.protocol === 'file:' 
    ? 'http://localhost:8000/api/v1/admin' 
    : '/api/v1/admin';

let API_KEY = '';
let albums = [];
let currentAlbumId = null;

/* ── Auth ─────────────────────────────────────────────────────── */
function login() {
    const key = document.getElementById('apiKeyInput').value.trim();
    if (!key) return showError('loginError', 'Please enter an API key');
    API_KEY = key;
    // Verify by fetching albums
    fetchAlbums()
        .then(() => {
            document.getElementById('loginScreen').classList.remove('active');
            document.getElementById('dashboard').classList.add('active');
        })
        .catch(() => showError('loginError', 'Invalid API key or server unreachable'));
}

document.getElementById('apiKeyInput').addEventListener('keydown', e => {
    if (e.key === 'Enter') login();
});

function logout() {
    API_KEY = '';
    albums = [];
    document.getElementById('dashboard').classList.remove('active');
    document.getElementById('loginScreen').classList.add('active');
    document.getElementById('apiKeyInput').value = '';
}

/* ── API helpers ──────────────────────────────────────────────── */
function headers() {
    return { 'X-Admin-API-Key': API_KEY };
}

async function fetchAlbums() {
    const res = await fetch(`${API}/albums`, { headers: headers() });
    if (!res.ok) throw new Error('Unauthorized');
    albums = await res.json();
    renderAlbums();
    return albums;
}

function refreshAlbums() {
    fetchAlbums()
        .then(() => toast('Albums refreshed', 'success'))
        .catch(() => toast('Failed to refresh', 'error'));
}

/* ── Render Albums ────────────────────────────────────────────── */
function renderAlbums() {
    const grid = document.getElementById('albumsGrid');
    const empty = document.getElementById('emptyState');

    // Stats
    document.getElementById('statAlbums').textContent = albums.length;
    document.getElementById('statFrames').textContent = albums.reduce((s, a) => s + a.total_frames, 0);
    document.getElementById('statLatest').textContent = albums.length ? albums[albums.length - 1].name : '—';

    if (!albums.length) {
        grid.innerHTML = '';
        empty.classList.remove('hidden');
        return;
    }
    empty.classList.add('hidden');

    grid.innerHTML = albums.map(a => `
        <div class="album-card" onclick="viewAlbum('${a.id}')">
            <div class="album-card-header">
                <span class="album-card-title">${esc(a.name)}</span>
                <span class="album-card-code">${esc(a.access_code)}</span>
            </div>
            <div class="album-card-meta">
                <span><i data-lucide="image"></i> ${a.total_frames} frames</span>
                <span><i data-lucide="calendar"></i> ${formatDate(a.created_at)}</span>
            </div>
            <div class="album-card-actions">
                <button class="btn btn-outline btn-sm" onclick="event.stopPropagation(); viewAlbum('${a.id}')"><i data-lucide="info" class="icon-sm"></i> Details</button>
                <button class="btn btn-danger btn-sm" onclick="event.stopPropagation(); deleteAlbum('${a.id}', '${esc(a.name)}')"><i data-lucide="trash" class="icon-sm"></i> Delete</button>
            </div>
        </div>
    `).join('');
    
    // Re-initialize icons for newly added HTML
    if (window.lucide) window.lucide.createIcons();
}

/* ── View Album Detail ────────────────────────────────────────── */
function viewAlbum(id) {
    const a = albums.find(x => x.id === id);
    if (!a) return;
    currentAlbumId = id;

    document.getElementById('detailTitle').textContent = a.name;

    document.getElementById('detailContent').innerHTML = `
        <div class="detail-info">
            <span class="detail-label">Album ID</span><span>${a.id}</span>
            <span class="detail-label">Access Code</span><span>${esc(a.access_code)}</span>
            <span class="detail-label">Frames</span><span>${a.total_frames}</span>
            <span class="detail-label">Created</span><span>${formatDate(a.created_at)}</span>
        </div>
    `;

    // Editable frame mappings
    const container = document.getElementById('editMappingsContainer');
    container.innerHTML = a.frames.map((f, i) => `
        <div class="frame-row">
            <span title="${esc(f.photo_id)}"><i data-lucide="image" class="icon-sm"></i> ${esc(f.photo_id)}</span>
            <input type="text" id="editStart_${i}" value="${toMMSS(f.start_time)}" placeholder="M:SS">
            <input type="text" id="editEnd_${i}" value="${toMMSS(f.end_time)}" placeholder="M:SS">
        </div>
    `).join('');

    document.getElementById('detailModal').classList.remove('hidden');
    if (window.lucide) window.lucide.createIcons();
}

function hideDetailModal() {
    document.getElementById('detailModal').classList.add('hidden');
    currentAlbumId = null;
}

async function saveEditedMappings() {
    if (!currentAlbumId) return;
    const a = albums.find(x => x.id === currentAlbumId);
    if (!a) return;

    const frames = a.frames.map((f, i) => ({
        photoId: f.photo_id,
        startTime: document.getElementById(`editStart_${i}`).value,
        endTime: document.getElementById(`editEnd_${i}`).value,
    }));

    try {
        const res = await fetch(`${API}/albums/${currentAlbumId}/map-frames`, {
            method: 'PUT',
            headers: { ...headers(), 'Content-Type': 'application/json' },
            body: JSON.stringify({ frames }),
        });
        if (!res.ok) throw new Error(await res.text());
        toast('Mappings saved!', 'success');
        hideDetailModal();
        await fetchAlbums();
    } catch (e) {
        toast('Failed to save: ' + e.message, 'error');
    }
}

/* ── Delete Album ─────────────────────────────────────────────── */
async function deleteAlbum(id, name) {
    if (!confirm(`Delete album "${name}"? This cannot be undone.`)) return;
    try {
        const res = await fetch(`${API}/albums/${id}`, {
            method: 'DELETE',
            headers: headers(),
        });
        if (!res.ok) throw new Error(await res.text());
        toast('Album deleted', 'success');
        await fetchAlbums();
    } catch (e) {
        toast('Delete failed: ' + e.message, 'error');
    }
}

/* ── Upload View (Formerly Modal) ─────────────────────────────── */
function showUploadView() {
    document.getElementById('homeView').classList.add('hidden');
    document.getElementById('uploadView').classList.remove('hidden');
    document.getElementById('uploadForm').reset();
    document.getElementById('frameMappingsSection').classList.add('hidden');
    document.getElementById('uploadProgress').classList.add('hidden');
    document.getElementById('videoFileName').classList.add('hidden');
    document.getElementById('photoCount').classList.add('hidden');
    document.querySelector('#videoDrop .file-drop-text').classList.remove('hidden');
    document.querySelector('#photoDrop .file-drop-text').classList.remove('hidden');
    document.querySelector('#videoDrop .drop-icon').classList.remove('hidden');
    document.querySelector('#photoDrop .drop-icon').classList.remove('hidden');
}

function hideUploadView() {
    document.getElementById('uploadView').classList.add('hidden');
    document.getElementById('homeView').classList.remove('hidden');
}

// File drop zones
function setupFileDrop(dropId, inputId, onFiles) {
    const drop = document.getElementById(dropId);
    const input = document.getElementById(inputId);

    drop.addEventListener('click', () => input.click());
    drop.addEventListener('dragover', e => { e.preventDefault(); drop.classList.add('dragover'); });
    drop.addEventListener('dragleave', () => drop.classList.remove('dragover'));
    drop.addEventListener('drop', e => {
        e.preventDefault();
        drop.classList.remove('dragover');
        input.files = e.dataTransfer.files;
        onFiles(input.files);
    });
    input.addEventListener('change', () => onFiles(input.files));
}

setupFileDrop('videoDrop', 'videoFile', files => {
    if (files.length) {
        document.getElementById('videoFileName').innerHTML = `<i data-lucide="check-circle" class="icon-sm" style="display:inline-block;vertical-align:middle;"></i> ${files[0].name} (${formatSize(files[0].size)})`;
        document.getElementById('videoFileName').classList.remove('hidden');
        document.querySelector('#videoDrop .file-drop-text').classList.add('hidden');
        document.querySelector('#videoDrop .drop-icon').classList.add('hidden');
        if (window.lucide) window.lucide.createIcons();
    }
});

setupFileDrop('photoDrop', 'photoFiles', files => {
    if (files.length) {
        document.getElementById('photoCount').innerHTML = `<i data-lucide="check-circle" class="icon-sm" style="display:inline-block;vertical-align:middle;"></i> ${files.length} photo${files.length > 1 ? 's' : ''} selected`;
        document.getElementById('photoCount').classList.remove('hidden');
        document.querySelector('#photoDrop .file-drop-text').classList.add('hidden');
        document.querySelector('#photoDrop .drop-icon').classList.add('hidden');
        if (window.lucide) window.lucide.createIcons();
        buildFrameMappings(files);
    }
});

function buildFrameMappings(files) {
    const section = document.getElementById('frameMappingsSection');
    const container = document.getElementById('frameMappingsContainer');
    section.classList.remove('hidden');

    container.innerHTML = Array.from(files).map((f, i) => `
        <div class="frame-row">
            <span title="${esc(f.name)}"><i data-lucide="image" class="icon-sm" style="color:var(--cta)"></i> ${esc(f.name)}</span>
            <input type="text" id="mapStart_${i}" placeholder="0:00" value="0:00">
            <input type="text" id="mapEnd_${i}" placeholder="0:10" value="0:10">
        </div>
    `).join('');
    if (window.lucide) window.lucide.createIcons();
    
    // Smooth scroll to timestamps when photos are added
    setTimeout(() => {
        section.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    }, 100);
}

/* ── Upload Album ─────────────────────────────────────────────── */
async function uploadAlbum(e) {
    e.preventDefault();

    const name = document.getElementById('albumName').value.trim();
    const code = document.getElementById('accessCode').value.trim().toUpperCase();
    const desc = document.getElementById('description').value.trim();
    const video = document.getElementById('videoFile').files[0];
    const photos = document.getElementById('photoFiles').files;

    if (!name || !code || !video || !photos.length) {
        toast('Please fill all required fields', 'error');
        return;
    }

    // Build frame_mappings JSON
    const mappings = Array.from(photos).map((f, i) => ({
        photoId: f.name,
        startTime: document.getElementById(`mapStart_${i}`)?.value || '0:00',
        endTime: document.getElementById(`mapEnd_${i}`)?.value || '0:10',
    }));

    const formData = new FormData();
    formData.append('name', name);
    formData.append('access_code', code);
    if (desc) formData.append('description', desc);
    formData.append('video', video);
    for (const photo of photos) {
        formData.append('photos', photo);
    }
    formData.append('frame_mappings', JSON.stringify(mappings));

    // Show progress
    const progressSection = document.getElementById('uploadProgress');
    const progressFill = document.getElementById('progressFill');
    const uploadStatus = document.getElementById('uploadStatus');
    const uploadBtn = document.getElementById('uploadBtn');
    progressSection.classList.remove('hidden');
    uploadBtn.disabled = true;
    uploadBtn.textContent = 'Uploading…';

    try {
        const xhr = new XMLHttpRequest();
        xhr.open('POST', `${API}/upload-album`);
        xhr.setRequestHeader('X-Admin-API-Key', API_KEY);

        xhr.upload.onprogress = (ev) => {
            if (ev.lengthComputable) {
                const pct = Math.round((ev.loaded / ev.total) * 100);
                progressFill.style.width = pct + '%';
                uploadStatus.textContent = pct < 100
                    ? `Uploading… ${pct}%`
                    : 'Processing (generating embeddings)…';
            }
        };

        const result = await new Promise((resolve, reject) => {
            xhr.onload = () => {
                if (xhr.status >= 200 && xhr.status < 300) {
                    resolve(JSON.parse(xhr.responseText));
                } else {
                    reject(new Error(xhr.responseText || 'Upload failed'));
                }
            };
            xhr.onerror = () => reject(new Error('Network error'));
            xhr.send(formData);
        });

        toast(`Album created! ID: ${result.album_id}`, 'success');
        hideUploadModal();
        await fetchAlbums();

    } catch (err) {
        toast('Upload failed: ' + err.message, 'error');
    } finally {
        uploadBtn.disabled = false;
        uploadBtn.textContent = 'Upload Album';
        progressFill.style.width = '0%';
        progressSection.classList.add('hidden');
    }
}

/* ── Helpers ───────────────────────────────────────────────────── */
function esc(s) {
    const d = document.createElement('div');
    d.textContent = s || '';
    return d.innerHTML;
}

function formatDate(d) {
    try {
        const dt = new Date(d);
        if (isNaN(dt.getTime())) return d;
        return dt.toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' });
    } catch { return d; }
}

function formatSize(bytes) {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / 1048576).toFixed(1) + ' MB';
}

function toMMSS(seconds) {
    const s = parseFloat(seconds) || 0;
    const m = Math.floor(s / 60);
    const ss = Math.floor(s % 60);
    return `${m}:${String(ss).padStart(2, '0')}`;
}

function showError(id, msg) {
    const el = document.getElementById(id);
    el.textContent = msg;
    el.classList.remove('hidden');
    setTimeout(() => el.classList.add('hidden'), 4000);
}

function toast(msg, type = 'success') {
    const el = document.getElementById('toast');
    el.textContent = msg;
    el.className = `toast show ${type}`;
    setTimeout(() => el.classList.remove('show'), 3500);
}
