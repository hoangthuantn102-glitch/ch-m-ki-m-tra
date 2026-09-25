import re

with open('./app/src/main/java/com/example/network/LocalServer.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Replace <main> block
main_start_tag = '<main class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">'
main_end_tag = '</main>'

new_main_block = """    <main class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <div class="grid grid-cols-1 lg:grid-cols-12 gap-8">
            
            <!-- Left Panel: Remote Controls & Camera & Configs (Occupies most space) -->
            <div class="lg:col-span-8 xl:col-span-9 flex flex-col space-y-6">
                
                <!-- PC Remote Controls & Camera Merged -->
                <div class="bg-indigo-50 border border-indigo-100 p-6 rounded-2xl shadow-sm flex flex-col">
                    <h2 class="text-xl font-bold mb-3 flex items-center space-x-2 text-indigo-900">
                        <span>🎮</span>
                        <span>Điều Khiển Điện Thoại Từ PC</span>
                    </h2>
                    <p class="text-sm text-indigo-700/80 mb-4">Theo dõi camera và điều khiển hoạt động chấm thi trực tiếp.</p>
                    
                    <!-- Camera View -->
                    <div class="relative bg-slate-900 rounded-2xl p-2 border border-slate-800 flex flex-col items-center justify-center min-h-[450px] lg:min-h-[550px] mb-6 overflow-hidden">
                        <img id="pc-live-camera-feed" src="" class="hidden max-h-[700px] w-full object-contain rounded-xl" alt="Live Camera Feed" />
                        <div id="pc-live-camera-placeholder" class="text-center p-6 text-slate-400">
                            <span class="text-5xl block mb-3">📷</span>
                            <span id="pc-camera-status" class="text-base">Đang chờ tín hiệu camera từ điện thoại...</span>
                        </div>
                        <div class="absolute top-4 right-4 bg-slate-800/80 backdrop-blur-md px-3 py-1.5 rounded-full text-xs text-emerald-400 font-semibold flex items-center gap-1.5 border border-slate-700">
                            <span class="w-2.5 h-2.5 rounded-full bg-emerald-500 animate-pulse"></span> Live Sync
                        </div>
                    </div>

                    <!-- Remote Actions -->
                    <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                        <button type="button" onclick="triggerRemoteAction('capture')" class="bg-indigo-600 hover:bg-indigo-700 text-white font-bold py-3.5 px-4 rounded-xl shadow-md transition-all flex items-center justify-center space-x-2">
                            <span class="text-xl">📸</span>
                            <span class="text-sm font-semibold">Chụp Bài Ngay</span>
                        </button>
                        <button type="button" onclick="triggerRemoteAction('next')" class="bg-emerald-600 hover:bg-emerald-700 text-white font-bold py-3.5 px-4 rounded-xl shadow-md transition-all flex items-center justify-center space-x-2">
                            <span class="text-xl">➡️</span>
                            <span class="text-sm font-semibold">Chấm Bài Tiếp</span>
                        </button>
                        <button type="button" onclick="triggerRemoteAction('toggle_auto')" id="remote-auto-btn" class="sm:col-span-2 lg:col-span-1 bg-white hover:bg-slate-50 text-indigo-700 border border-indigo-200 font-bold py-3.5 px-4 rounded-xl shadow-sm transition-all text-sm flex justify-center items-center space-x-2">
                            <span class="text-xl">🤖</span>
                            <span>Bật/Tắt Tự Động Quét</span>
                        </button>
                    </div>

                    <div id="remote-grading-loader" class="hidden flex flex-col items-center justify-center p-6 space-y-3 mt-4">
                        <div class="w-10 h-10 border-4 border-indigo-600 border-t-transparent rounded-full animate-spin"></div>
                        <span class="text-sm text-indigo-700 font-medium">Đang truyền ảnh sang máy & AI đang chấm...</span>
                        <span class="text-xs text-indigo-500">(Quá trình này có thể mất 10-15 giây)</span>
                    </div>
                </div>

                <!-- Configs Row -->
                <div class="grid grid-cols-1 xl:grid-cols-2 gap-6">
                    <!-- Selected Class & Exam Config -->
                    <div class="bg-white p-6 rounded-2xl shadow-sm border border-slate-100 flex flex-col">
                        <h2 class="text-lg font-bold mb-3 flex items-center space-x-2 text-slate-900">
                            <span>🏫</span>
                            <span>Lớp & Bài Kiểm Tra Đang Chấm</span>
                        </h2>
                        <p class="text-xs text-slate-500 mb-4">Chọn đề thi và lớp học tương ứng được đồng bộ từ điện thoại để đối chiếu đáp án chính xác.</p>
                        
                        <div class="space-y-3">
                            <div>
                                <select id="config-selector" onchange="selectSavedConfig(this.value)" class="w-full bg-slate-50 border border-slate-200 rounded-xl px-3 py-2.5 text-xs text-slate-800 focus:outline-none focus:ring-2 focus:ring-indigo-500">
                                    <option value="">-- Đang tải danh sách đề thi --</option>
                                </select>
                            </div>
                        </div>

                        <h2 class="text-sm font-bold mt-8 mb-2 flex items-center space-x-2 text-slate-900">
                            <span>📤</span>
                            <span>Hoặc Tải File Ảnh Từ Máy Tính</span>
                        </h2>
                        
                        <div id="remote-upload-area" class="border-2 border-dashed border-slate-200 rounded-2xl p-4 text-center cursor-pointer hover:border-indigo-500 hover:bg-slate-50/50 transition-all flex flex-col items-center justify-center space-y-1 flex-1">
                            <span class="text-2xl">📤</span>
                            <span class="text-xs font-medium text-slate-600">Kéo thả hoặc Click để chọn ảnh bài thi</span>
                            <span class="text-[10px] text-slate-400">Định dạng JPEG, PNG</span>
                            <input type="file" id="remote-image-input" accept="image/*" class="hidden" onchange="handleRemoteImageUpload(this)">
                        </div>
                    </div>

                    <!-- Cấu Hình Chi Tiết -->
                    <div class="bg-white p-6 rounded-2xl shadow-sm border border-slate-100">
                        <h2 class="text-lg font-bold mb-4 flex items-center space-x-2 text-slate-900">
                            <span>⚙️</span>
                            <span>Cấu Hình Chi Tiết</span>
                        </h2>
                        
                        <form id="settings-form" class="space-y-4">
                            <div>
                                <label class="block text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1">Loại Hình Bài Thi</label>
                                <select id="setting-test-type" class="w-full bg-slate-50 border border-slate-200 rounded-xl px-3 py-2.5 text-slate-800 focus:outline-none focus:ring-2 focus:ring-indigo-500">
                                    <option value="ANSWER_TABLE">Học sinh điền đáp án vào BẢNG/Ô ĐÁP ÁN</option>
                                    <option value="CIRCLED_ON_SHEET">Học sinh khoanh tròn TRÊN ĐỀ THI</option>
                                </select>
                            </div>
                            
                            <div class="grid grid-cols-3 gap-3">
                                <div>
                                    <label class="block text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1">Số Trang Bài Thi</label>
                                    <select id="setting-total-pages" class="w-full bg-slate-50 border border-slate-200 rounded-xl px-3 py-2 text-slate-800 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500">
                                        <option value="1">1 Trang</option>
                                        <option value="2">2 Trang</option>
                                        <option value="3">3 Trang</option>
                                        <option value="4">4 Trang</option>
                                        <option value="5">5 Trang</option>
                                    </select>
                                </div>
                                <div>
                                    <label class="block text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1">Tổng Số Câu</label>
                                    <input type="number" id="setting-total-questions" min="1" max="100" class="w-full bg-slate-50 border border-slate-200 rounded-xl px-3 py-2 text-slate-800 focus:outline-none focus:ring-2 focus:ring-indigo-500">
                                </div>
                                <div>
                                    <label class="block text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1">Điểm / Câu</label>
                                    <input type="number" id="setting-points" step="0.05" min="0.01" class="w-full bg-slate-50 border border-slate-200 rounded-xl px-3 py-2 text-slate-800 focus:outline-none focus:ring-2 focus:ring-indigo-500">
                                </div>
                            </div>

                            <div>
                                <label class="block text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1">Lựa Chọn Chấm Thi</label>
                                <div class="flex flex-wrap gap-1.5 p-3 bg-slate-50 border border-slate-200 rounded-xl max-h-24 overflow-y-auto" id="selected-questions-container">
                                </div>
                            </div>

                            <div>
                                <div class="flex justify-between items-center mb-1">
                                    <label class="block text-xs font-semibold text-slate-500 uppercase tracking-wider">Khóa Đáp Án (Master Key)</label>
                                    <button type="button" onclick="triggerCsvUpload()" class="text-xs font-medium text-indigo-600 hover:text-indigo-800">📥 File CSV</button>
                                    <input type="file" id="csv-file-input" accept=".csv, .txt" class="hidden" onchange="handleCsvFile(this)">
                                </div>
                                <textarea id="setting-master-keys" rows="4" class="w-full bg-slate-50 border border-slate-200 font-mono text-sm rounded-xl p-3 text-slate-800 focus:outline-none focus:ring-2 focus:ring-indigo-500 placeholder-slate-300" placeholder="1,A&#10;2,B&#10;3,C&#10;..."></textarea>
                            </div>

                            <button type="submit" class="w-full bg-indigo-600 hover:bg-indigo-700 text-white font-semibold py-2.5 px-4 rounded-xl shadow-sm transition-all text-sm flex justify-center items-center space-x-2">
                                <span>💾</span>
                                <span>Cập Nhật Tới ĐT</span>
                            </button>
                        </form>
                    </div>
                </div>
            </div>

            <!-- Right Panel: Historical Results Table -->
            <div class="lg:col-span-4 xl:col-span-3 flex flex-col space-y-6">
                <div class="bg-white p-6 rounded-2xl shadow-sm border border-slate-100 flex flex-col h-[850px]">
                    <div class="flex justify-between items-start mb-6">
                        <div>
                            <h2 class="text-xl font-bold text-slate-900 flex items-center space-x-2">
                                <span>📊</span>
                                <span>Kết Quả Chấm</span>
                            </h2>
                            <p class="text-xs text-slate-500 mt-1">Lịch sử chấm thi.</p>
                        </div>
                        <button onclick="clearAllResults()" class="text-xs font-semibold text-rose-600 hover:text-white hover:bg-rose-600 border border-rose-200 px-3 py-2 rounded-xl transition-all" title="Xóa Toàn Bộ Lịch Sử">
                            🗑️ Xóa
                        </button>
                    </div>

                    <!-- Search / Quick Filter -->
                    <div class="mb-4">
                        <input type="text" id="search-input" oninput="filterResults()" placeholder="Tìm kiếm tên..." class="w-full bg-slate-50 border border-slate-200 rounded-xl px-4 py-2 text-sm text-slate-800 focus:outline-none focus:ring-2 focus:ring-indigo-500">
                    </div>

                    <!-- Table (Vertical/Compact layout) -->
                    <div class="overflow-y-auto flex-1 rounded-xl bg-slate-50 p-2 space-y-3" id="results-table-body">
                    </div>
                </div>
            </div>
        </div>
    </main>"""

start_idx = content.find(main_start_tag)
end_idx = content.find(main_end_tag, start_idx) + len(main_end_tag)
if start_idx != -1 and end_idx != -1:
    content = content[:start_idx] + new_main_block + content[end_idx:]
    print("Replaced <main> block")
else:
    print("Could not find <main> block")

# 2. Replace populateResultsUI function
func_start = "function populateResultsUI(historyList) {"
func_end = "}" # Need to find the exact end block

# A bit hacky, but we can use regex to replace the function body
import re
pattern = r'function populateResultsUI\(historyList\) \{.*?\n        \}'
replacement = r'''function populateResultsUI(historyList) {
            const body = document.getElementById('results-table-body');
            body.innerHTML = '';

            if (historyList.length === 0) {
                body.innerHTML = `
                    <div class="p-8 text-center text-sm text-slate-400 border border-dashed border-slate-300 rounded-xl">Chưa có bài thi nào được chấm</div>
                `;
                return;
            }

            historyList.forEach(item => {
                const dateText = formatDate(item.timestamp);
                const typeText = item.testType === "ANSWER_TABLE" ? "Bảng Đáp Án" : "Khoanh Trên Đề";

                const card = document.createElement('div');
                card.className = "bg-white p-4 rounded-xl shadow-sm border border-slate-100 hover:border-indigo-300 transition-all cursor-pointer";
                card.onclick = () => openResultDetail(item.id);
                
                card.innerHTML = `
                    <div class="flex justify-between items-start mb-2">
                        <div class="font-bold text-slate-900">${d}{escapeHtml(item.studentName)}</div>
                        <div class="text-indigo-600 font-bold font-mono bg-indigo-50 px-2 py-0.5 rounded-md text-sm">${d}{item.score.toFixed(2)} đ</div>
                    </div>
                    <div class="flex justify-between items-center text-xs text-slate-500">
                        <div>
                            <span class="bg-slate-100 text-slate-600 px-1.5 py-0.5 rounded mr-1">${d}{typeText}</span>
                        </div>
                        <div class="font-mono">${d}{item.correctCount}/${d}{item.gradedCount} câu</div>
                    </div>
                    <div class="mt-2 text-right text-[10px] text-slate-400">${d}{dateText}</div>
                `;
                body.appendChild(card);
            });
        }'''

# Since ${d} is defined as '$' in Kotlin, it will inject properly
content = re.sub(pattern, replacement, content, flags=re.DOTALL)

with open('./app/src/main/java/com/example/network/LocalServer.kt', 'w', encoding='utf-8') as f:
    f.write(content)

print("Done")
