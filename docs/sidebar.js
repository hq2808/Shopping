document.addEventListener('DOMContentLoaded', () => {
    // 1. Dynamic CSS Injection (Only once)
    if (!document.getElementById('injected-premium-styles')) {
        const style = document.createElement('style');
        style.id = 'injected-premium-styles';
        style.textContent = `
            /* Checkbox track styling */
            .topic-header {
                display: flex;
                align-items: center;
                gap: 12px;
                margin-top: 2rem;
                border-bottom: 1px solid var(--border-color);
                padding-bottom: 8px;
            }
            .topic-check {
                width: 20px;
                height: 20px;
                accent-color: var(--color-success);
                cursor: pointer;
                border-radius: 4px;
            }
            
            /* Copy button styling */
            .code-container {
                position: relative;
            }
            .copy-btn {
                position: absolute;
                top: 8px;
                right: 8px;
                background: rgba(255, 255, 255, 0.05);
                border: 1px solid var(--border-color);
                color: var(--text-muted);
                padding: 4px 8px;
                border-radius: 4px;
                font-size: 12px;
                cursor: pointer;
                transition: var(--transition-smooth);
                font-family: var(--font-sans);
                font-weight: 500;
            }
            .copy-btn:hover {
                background: rgba(255, 255, 255, 0.1);
                color: var(--text-primary);
            }
            
            /* Interactive details styling */
            details {
                background: rgba(255, 255, 255, 0.01);
                border: 1px solid var(--border-color);
                border-radius: 8px;
                margin-bottom: 12px;
                overflow: hidden;
                transition: var(--transition-smooth);
            }
            details[open] {
                border-color: rgba(129, 140, 248, 0.3);
                background: rgba(255, 255, 255, 0.02);
            }
            summary {
                padding: 14px 20px;
                font-weight: 600;
                cursor: pointer;
                outline: none;
                user-select: none;
                display: flex;
                justify-content: space-between;
                align-items: center;
                color: var(--text-primary);
            }
            summary::-webkit-details-marker {
                display: none;
            }
            summary::after {
                content: "＋";
                font-size: 16px;
                color: var(--text-muted);
            }
            details[open] summary::after {
                content: "－";
            }
            .details-content {
                padding: 16px 20px;
                border-top: 1px solid var(--border-color);
                color: var(--text-secondary);
            }
            
            /* Q&A Block styling */
            .qa-block {
                background: rgba(129, 140, 248, 0.02);
                border: 1px solid rgba(129, 140, 248, 0.1);
                border-radius: 8px;
                padding: 20px;
                margin-bottom: 20px;
            }
            .qa-question {
                font-weight: 700;
                color: var(--text-primary);
                margin-bottom: 8px;
                display: flex;
                gap: 8px;
            }
            .qa-question::before {
                content: "Q:";
                color: var(--color-primary);
            }
            .qa-answer {
                color: var(--text-secondary);
                padding-left: 20px;
                position: relative;
            }
            .qa-answer::before {
                content: "A:";
                color: var(--color-success);
                position: absolute;
                left: 0;
                font-weight: 700;
            }
            
            /* Dashboard progress bar styling */
            .progress-container {
                background: rgba(255, 255, 255, 0.04);
                border-radius: 8px;
                height: 12px;
                width: 100%;
                margin-top: 8px;
                overflow: hidden;
                border: 1px solid var(--border-color);
            }
            .progress-bar {
                height: 100%;
                background: linear-gradient(90deg, var(--color-success), var(--color-primary));
                width: 0%;
                transition: width 0.4s ease;
            }
        `;
        document.head.appendChild(style);
    }

    // 2. Generate Sidebar Menu (Only once on initial load)
    generateSidebar();

    // 3. Initialize Interactive Components (Checkboxes, copy buttons, etc.)
    initializePage();

    // 4. Set up Link Interceptor for SPA routing
    setupSpaRouter();
});

// Render the sidebar menu structure dynamically
function generateSidebar() {
    const sidebarEl = document.querySelector('.sidebar');
    if (!sidebarEl) return;

    // Detect folder path depth
    const pathname = window.location.pathname;
    let prefix = '';
    let isDev = false;
    let isQa = false;

    if (pathname.includes('/dev/')) {
        isDev = true;
        prefix = '../';
    } else if (pathname.includes('/qa/')) {
        isQa = true;
        prefix = '../';
    }

    // Generate path prefixes
    const rootPath = prefix + 'index.html';
    const qaPrefix = isQa ? '' : (isDev ? '../qa/' : 'qa/');
    const devPrefix = isDev ? '' : (isQa ? '../dev/' : 'dev/');

    // Get current file name
    const currentFile = pathname.split('/').pop() || 'index.html';

    // Construct the menu structure
    const menu = [
        {
            title: "Tổng quan",
            items: [
                { text: "Trang chủ cổng tài liệu", file: "index.html", path: rootPath, id: "nav-index" }
            ]
        },
        {
            title: "Dành cho QA / Tester",
            items: [
                { text: "1. Hướng dẫn khởi chạy", file: "run_guide.html", path: qaPrefix + "run_guide.html", id: "nav-run" },
                { text: "2. Tài liệu đặc tả dự án", file: "project_spec.html", path: qaPrefix + "project_spec.html", id: "nav-project-spec" },
                { text: "3. Danh sách Test Cases", file: "test_cases.html", path: qaPrefix + "test_cases.html", id: "nav-test-cases" },
                { text: "4. Quản lý Test Data", file: "test_data_guide.html", path: qaPrefix + "test_data_guide.html", id: "nav-test-data" },
                { text: "5. Hướng dẫn kiểm thử", file: "testing_guide.html", path: qaPrefix + "testing_guide.html", id: "nav-testing" },
                { text: "6. Postman Collection", file: "postman_guide.html", path: qaPrefix + "postman_guide.html", id: "nav-postman" },
                { text: "7. Kiểm thử kiến trúc SA", file: "sa_test_guide.html", path: qaPrefix + "sa_test_guide.html", id: "nav-sa-test" },
                { text: "8. Automation Testing Setup", file: "automation_guide.html", path: qaPrefix + "automation_guide.html", id: "nav-automation" },
                { text: "9. Performance Benchmark", file: "performance_benchmark.html", path: qaPrefix + "performance_benchmark.html", id: "nav-performance" },
                { text: "10. Mẫu Bug Report chuẩn", file: "bug_report_template.html", path: qaPrefix + "bug_report_template.html", id: "nav-bug-report" },
                { text: "11. Checklist trước Release", file: "release_checklist.html", path: qaPrefix + "release_checklist.html", id: "nav-release-checklist" }
            ]
        },
        {
            title: "Dành cho Dev / Architect",
            items: [
                { text: "Đánh giá chịu tải DB", file: "scalability_audit.html", path: devPrefix + "scalability_audit.html", id: "nav-scalability" },
                { text: "Giải pháp chịu tải cực hạn", file: "concurrency_solution.html", path: devPrefix + "concurrency_solution.html", id: "nav-concurrency" },
                { text: "Phỏng vấn: Sync Data", file: "sync_interview.html", path: devPrefix + "sync_interview.html", id: "nav-sync-interview" },
                { text: "Phỏng vấn: Migrate DB", file: "db_migration_interview.html", path: devPrefix + "db_migration_interview.html", id: "nav-db-migration" },
                { text: "☕ Java Core & JVM Internals", file: "java_core_jvm.html", path: devPrefix + "java_core_jvm.html", id: "nav-java-core" },
                { text: "✨ Clean Code & Refactoring", file: "dev_clean_code.html", path: devPrefix + "dev_clean_code.html", id: "nav-java-clean" },
                { text: "🌐 HTTP & Spring Security", file: "http_spring_security.html", path: devPrefix + "http_spring_security.html", id: "nav-java-http" },
                { text: "💾 Database & JPA/Mongo", file: "database_mongodb_jpa.html", path: devPrefix + "database_mongodb_jpa.html", id: "nav-java-db" },
                { text: "⚡ Async & Task Queues", file: "async_message_queues.html", path: devPrefix + "async_message_queues.html", id: "nav-java-async" },
                { text: "🚀 Swag Business Flows", file: "swag_business_flows.html", path: devPrefix + "swag_business_flows.html", id: "nav-java-swag" }
            ]
        }
    ];

    let sidebarHTML = '';
    menu.forEach(section => {
        sidebarHTML += `<div class="sidebar-title">${section.title}</div>`;
        sidebarHTML += `<ul class="sidebar-menu">`;
        section.items.forEach(item => {
            const isActive = currentFile === item.file;
            sidebarHTML += `
                <li class="sidebar-item">
                    <a href="${item.path}" class="sidebar-link${isActive ? ' active' : ''}" id="${item.id}">${item.text}</a>
                </li>`;
        });
        sidebarHTML += `</ul>`;
    });

    sidebarEl.innerHTML = sidebarHTML;
}

// Set up page event handlers (runs once on load, and after every SPA navigation)
function initializePage() {
    // 1. Setup Code Copy Buttons
    const preBlocks = document.querySelectorAll('.code-container');
    preBlocks.forEach(container => {
        if (container.querySelector('.copy-btn')) return;

        const pre = container.querySelector('pre');
        if (!pre) return;
        
        const copyBtn = document.createElement('button');
        copyBtn.className = 'copy-btn';
        copyBtn.innerText = 'Copy';
        container.appendChild(copyBtn);

        copyBtn.addEventListener('click', () => {
            const code = pre.innerText;
            navigator.clipboard.writeText(code).then(() => {
                copyBtn.innerText = 'Copied!';
                copyBtn.style.background = 'rgba(52, 211, 153, 0.15)';
                copyBtn.style.borderColor = 'var(--color-success)';
                copyBtn.style.color = '#fff';
                setTimeout(() => {
                    copyBtn.innerText = 'Copy';
                    copyBtn.style.background = '';
                    copyBtn.style.borderColor = '';
                    copyBtn.style.color = '';
                }, 2000);
            });
        });
    });

    // 2. Persistent Checkbox Tracking per page
    const checkboxes = document.querySelectorAll('.topic-check');
    const pathname = window.location.pathname;
    const pageId = pathname.split('/').pop() || 'index.html';

    const updateProgressBar = () => {
        const total = checkboxes.length;
        if (total === 0) return;
        
        let checkedCount = 0;
        checkboxes.forEach(chk => {
            if (chk.checked) checkedCount++;
        });

        const percent = Math.round((checkedCount / total) * 100);
        const progressBar = document.querySelector('.progress-bar');
        const progressStats = document.getElementById('progress-stats');

        if (progressBar) {
            progressBar.style.width = `${percent}%`;
        }
        if (progressStats) {
            progressStats.innerText = `Tiến trình: ${percent}% hoàn thành (${checkedCount}/${total} chủ đề)`;
        }
    };

    checkboxes.forEach((chk, index) => {
        const key = `java_prep_${pageId}_chk_${index}`;
        if (localStorage.getItem(key) === 'true') {
            chk.checked = true;
        }

        // Remove existing listener reference if elements are re-loaded
        const newChk = chk.cloneNode(true);
        chk.parentNode.replaceChild(newChk, chk);

        newChk.addEventListener('change', () => {
            localStorage.setItem(key, newChk.checked);
            updateProgressBar();
            updateGlobalDashboard();
        });
    });

    updateProgressBar();
    updateGlobalDashboard();

    // 3. Global progress bar updater on index.html
    function updateGlobalDashboard() {
        const pages = [
            'java_core_jvm.html',
            'dev_clean_code.html',
            'http_spring_security.html',
            'database_mongodb_jpa.html',
            'async_message_queues.html',
            'swag_business_flows.html'
        ];
        
        const globalStats = document.getElementById('global-progress-stats');
        const globalBar = document.getElementById('global-progress-bar');
        if (!globalBar) return;

        const taskCounts = {
            'java_core_jvm.html': 6,
            'dev_clean_code.html': 7,
            'http_spring_security.html': 8,
            'database_mongodb_jpa.html': 7,
            'async_message_queues.html': 6,
            'swag_business_flows.html': 8
        };

        let total = 0;
        let checked = 0;

        pages.forEach(p => {
            const count = taskCounts[p] || 0;
            total += count;
            for (let i = 0; i < count; i++) {
                if (localStorage.getItem(`java_prep_${p}_chk_${i}`) === 'true') {
                    checked++;
                }
            }
        });

        const percent = total > 0 ? Math.round((checked / total) * 100) : 0;
        globalBar.style.width = `${percent}%`;
        if (globalStats) {
            globalStats.innerText = `Tổng quan tiến độ ôn tập: ${percent}% hoàn thành (${checked}/${total} chủ đề đã xong)`;
        }
    }
}

// Intercept all internal navigation link clicks for SPA feel
function setupSpaRouter() {
    document.addEventListener('click', event => {
        const anchor = event.target.closest('a');
        if (!anchor) return;
        
        const href = anchor.getAttribute('href');
        if (!href) return;
        
        const isExternal = href.startsWith('http://') || href.startsWith('https://') || anchor.getAttribute('target') === '_blank';
        const isAnchorOnly = href.startsWith('#');
        const isJavascript = href.startsWith('javascript:');
        
        if (!isExternal && !isAnchorOnly && !isJavascript) {
            event.preventDefault();
            loadPage(anchor.href);
        }
    });

    window.addEventListener('popstate', () => {
        loadPage(window.location.href, false);
    });
}

// Fetch and dynamically replace content wrapper
function loadPage(url, pushState = true) {
    fetch(url)
        .then(response => {
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            return response.text();
        })
        .then(html => {
            const parser = new DOMParser();
            const doc = parser.parseFromString(html, 'text/html');
            
            // Update the content area
            const newContent = doc.querySelector('.content-wrapper');
            const currentContent = document.querySelector('.content-wrapper');
            if (newContent && currentContent) {
                currentContent.innerHTML = newContent.innerHTML;
                currentContent.className = newContent.className;
            }
            
            // Update title
            if (doc.title) {
                document.title = doc.title;
            }

            // Push state to browser history
            if (pushState) {
                window.history.pushState({ url }, '', url);
            }
            
            // Re-generate the sidebar so relative link paths match the new folder depth!
            generateSidebar();
            
            // Scroll smoothly back to top
            window.scrollTo({ top: 0, behavior: 'smooth' });
            
            // Re-initialize code block copying and checkbox event listeners on new elements
            initializePage();
        })
        .catch(error => {
            console.error('Failed to load page content:', error);
            // Fallback: navigate normally if fetch fails
            if (pushState) {
                window.location.href = url;
            }
        });
}
