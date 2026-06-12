document.addEventListener('DOMContentLoaded', () => {
    // 1. Dynamic CSS Injection for UI elements not in the main style.css
    const style = document.createElement('style');
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

    // 2. Code Copy Button Setup
    const preBlocks = document.querySelectorAll('.code-container');
    preBlocks.forEach(container => {
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

    // 3. Persistent Checkbox Tracking per page
    const checkboxes = document.querySelectorAll('.topic-check');
    const pageId = window.location.pathname.split('/').pop() || 'index.html';

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

        chk.addEventListener('change', () => {
            localStorage.setItem(key, chk.checked);
            updateProgressBar();
            updateGlobalDashboard();
        });
    });

    updateProgressBar();

    // 4. Global progress bar updater on index.html
    function updateGlobalDashboard() {
        const pages = [
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

    if (pageId === 'index.html' || pageId === '') {
        updateGlobalDashboard();
    }
});
