// Vanilla Javascript for Supermarket Auto-Buy Dashboard

document.addEventListener('DOMContentLoaded', () => {
    // State
    let shoppingList = [];
    let allMappings = [];
    let credentialsStatus = { hasUsername: false, hasPassword: false, username: '' };
    let pollIntervalId = null;
    let lastState = 'IDLE';

    // DOM Elements
    const credStatusDot = document.getElementById('cred-status-dot');
    const credStatusText = document.getElementById('cred-status-text');
    const btnOpenCreds = document.getElementById('btn-open-creds');
    
    const listCountBadge = document.getElementById('list-count-badge');
    const addItemForm = document.getElementById('add-item-form');
    const newItemQuery = document.getElementById('new-item-query');
    const newItemQty = document.getElementById('new-item-qty');
    const shoppingListItems = document.getElementById('shopping-list-items');
    
    const executionStateBadge = document.getElementById('execution-state-badge');
    const runSettingsPanel = document.getElementById('run-settings-panel');
    const runSupermarket = document.getElementById('run-supermarket');
    const runHeadless = document.getElementById('run-headless');
    const btnStartRun = document.getElementById('btn-start-run');
    
    const activeRunPanel = document.getElementById('active-run-panel');
    const currentTaskLabel = document.getElementById('current-task-label');
    const currentTaskDetails = document.getElementById('current-task-details');
    const btnCancelRun = document.getElementById('btn-cancel-run');
    
    const btnClearLogs = document.getElementById('btn-clear-logs');
    const consoleLogLines = document.getElementById('console-log-lines');
    
    const mappingSearchInput = document.getElementById('mapping-search-input');
    const mappingsListContainer = document.getElementById('mappings-list-container');
    
    // Modals
    const credsModal = document.getElementById('creds-modal');
    const btnCloseCredsModal = document.getElementById('btn-close-creds-modal');
    const btnCancelCreds = document.getElementById('btn-cancel-creds');
    const credsForm = document.getElementById('creds-form');
    const credUsername = document.getElementById('cred-username');
    const credPassword = document.getElementById('cred-password');
    
    const resolveModal = document.getElementById('resolve-modal');
    const resolveQueryTitle = document.getElementById('resolve-query-title');
    const resolveProductsGrid = document.getElementById('resolve-products-grid');
    const btnSkipMapping = document.getElementById('btn-skip-mapping');
    
    const reviewModal = document.getElementById('review-modal');
    const btnCompleteRun = document.getElementById('btn-complete-run');

    // -------------------------------------------------------------
    // INITIALIZATION & LOADING
    // -------------------------------------------------------------

    function init() {
        loadShoppingList();
        loadMappings();
        checkCredentialsStatus();
        
        // Start polling immediately in case a run is already active
        startStatusPolling();
    }

    // -------------------------------------------------------------
    // CREDENTIALS MANAGEMENT
    // -------------------------------------------------------------

    async function checkCredentialsStatus() {
        try {
            const res = await fetch('/api/credentials?supermarket=CONTINENTE');
            if (res.ok) {
                credentialsStatus = await res.json();
                updateCredentialsBadge();
            }
        } catch (err) {
            console.error('Failed to check credentials status', err);
        }
    }

    function updateCredentialsBadge() {
        if (credentialsStatus.hasUsername && credentialsStatus.hasPassword) {
            credStatusDot.className = 'status-indicator-dot active';
            credStatusText.textContent = `Configured (${credentialsStatus.username})`;
        } else {
            credStatusDot.className = 'status-indicator-dot inactive';
            credStatusText.textContent = 'Not Configured';
        }
    }

    btnOpenCreds.addEventListener('click', () => {
        credUsername.value = credentialsStatus.username || '';
        credPassword.value = '';
        credsModal.style.display = 'flex';
    });

    btnCloseCredsModal.addEventListener('click', () => credsModal.style.display = 'none');
    btnCancelCreds.addEventListener('click', () => credsModal.style.display = 'none');

    credsForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const username = credUsername.value.trim();
        const password = credPassword.value;
        const supermarket = document.getElementById('cred-supermarket').value;

        try {
            const res = await fetch('/api/credentials', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ supermarket, username, password })
            });

            if (res.ok) {
                credsModal.style.display = 'none';
                addConsoleLog('SUCCESS', 'Credentials updated successfully.');
                checkCredentialsStatus();
            } else {
                const data = await res.json();
                alert('Failed to save credentials: ' + (data.message || 'Unknown error'));
            }
        } catch (err) {
            alert('Error communication with backend credentials API.');
        }
    });

    // -------------------------------------------------------------
    // SHOPPING LIST MANAGEMENT
    // -------------------------------------------------------------

    async function loadShoppingList() {
        try {
            const res = await fetch('/api/shopping-list');
            if (res.ok) {
                shoppingList = await res.json();
                renderShoppingList();
            }
        } catch (err) {
            console.error('Failed to load shopping list', err);
        }
    }

    function renderShoppingList() {
        listCountBadge.textContent = `${shoppingList.length} item${shoppingList.length === 1 ? '' : 's'}`;
        shoppingListItems.innerHTML = '';

        if (shoppingList.length === 0) {
            shoppingListItems.innerHTML = '<div class="empty-state-text">Your shopping list is empty.</div>';
            return;
        }

        shoppingList.forEach((item, index) => {
            const row = document.createElement('div');
            row.className = 'shopping-item-row';
            row.innerHTML = `
                <div class="item-info">
                    <span class="item-name">${escapeHtml(item.query)}</span>
                    <span class="item-qty-badge">Quantity: ${item.quantity}</span>
                </div>
                <div class="row-actions">
                    <button class="btn btn-secondary btn-small" onclick="adjustItemQty(${index}, -1)">-</button>
                    <button class="btn btn-secondary btn-small" onclick="adjustItemQty(${index}, 1)">+</button>
                    <button class="btn-trash" onclick="removeShoppingItem(${index})" title="Delete item">🗑️</button>
                </div>
            `;
            shoppingListItems.appendChild(row);
        });
    }

    window.adjustItemQty = (index, delta) => {
        const item = shoppingList[index];
        const newQty = item.quantity + delta;
        if (newQty >= 1) {
            shoppingList[index] = { ...item, quantity: newQty };
            renderShoppingList();
            saveShoppingListToServer();
        }
    };

    window.removeShoppingItem = (index) => {
        shoppingList.splice(index, 1);
        renderShoppingList();
        saveShoppingListToServer();
    };

    addItemForm.addEventListener('submit', (e) => {
        e.preventDefault();
        const query = newItemQuery.value.trim();
        const quantity = parseInt(newItemQty.value, 10);

        if (!query) return;

        // Check if item query already exists
        const existingIdx = shoppingList.findIndex(item => item.query.toLowerCase() === query.toLowerCase());
        if (existingIdx !== -1) {
            shoppingList[existingIdx] = {
                ...shoppingList[existingIdx],
                quantity: shoppingList[existingIdx].quantity + quantity
            };
        } else {
            shoppingList.push({ query, quantity });
        }

        newItemQuery.value = '';
        newItemQty.value = '1';
        renderShoppingList();
        saveShoppingListToServer();
    });

    async function saveShoppingListToServer() {
        try {
            const res = await fetch('/api/shopping-list', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(shoppingList)
            });
            if (res.ok) {
                console.log('Shopping list auto-saved.');
            } else {
                console.error('Failed to auto-save shopping list.');
            }
        } catch (err) {
            console.error('Error auto-saving shopping list', err);
        }
    }

    // -------------------------------------------------------------
    // PRODUCT MAPPINGS MANAGEMENT
    // -------------------------------------------------------------

    async function loadMappings() {
        try {
            const res = await fetch('/api/mappings');
            if (res.ok) {
                allMappings = await res.json();
                renderMappings();
            }
        } catch (err) {
            console.error('Failed to load product mappings', err);
        }
    }

    function renderMappings() {
        const query = mappingSearchInput.value.trim().toLowerCase();
        const filtered = allMappings.filter(m => 
            m.searchText.toLowerCase().includes(query) || 
            (m.productName && m.productName.toLowerCase().includes(query)) ||
            m.externalProductId.toLowerCase().includes(query)
        );

        mappingsListContainer.innerHTML = '';

        if (filtered.length === 0) {
            mappingsListContainer.innerHTML = '<div class="empty-state-text">No product mappings found.</div>';
            return;
        }

        filtered.forEach(mapping => {
            const row = document.createElement('div');
            row.className = 'mapping-row';
            row.innerHTML = `
                <div class="mapping-details">
                    <span class="mapping-query">"${escapeHtml(mapping.searchText)}"</span>
                    <span class="mapping-sku-name" title="${escapeHtml(mapping.productName || '')}">
                        SKU: ${escapeHtml(mapping.externalProductId)} ${mapping.productName ? `— ${escapeHtml(mapping.productName)}` : ''}
                    </span>
                </div>
                <button class="btn-trash" onclick="deleteMapping(${mapping.id})" title="Delete mapping">🗑️</button>
            `;
            mappingsListContainer.appendChild(row);
        });
    }

    window.deleteMapping = async (id) => {
        if (!confirm('Are you sure you want to delete this product SKU mapping?')) return;
        try {
            const res = await fetch(`/api/mappings/${id}`, { method: 'DELETE' });
            if (res.ok) {
                addConsoleLog('INFO', `Deleted product mapping ID ${id}`);
                loadMappings();
            } else {
                alert('Failed to delete mapping.');
            }
        } catch (err) {
            console.error(err);
        }
    };

    mappingSearchInput.addEventListener('input', renderMappings);

    // -------------------------------------------------------------
    // CONSOLE LOG LINES
    // -------------------------------------------------------------

    function addConsoleLog(level, message) {
        const line = document.createElement('div');
        line.className = `log-line log-${level.toLowerCase()}`;
        line.textContent = `[${new Date().toLocaleTimeString()}] ${message}`;
        consoleLogLines.appendChild(line);
        consoleLogLines.scrollTop = consoleLogLines.scrollHeight;
    }

    btnClearLogs.addEventListener('click', () => {
        consoleLogLines.innerHTML = '';
    });

    // -------------------------------------------------------------
    // EXECUTION ORCHESTRATION & POLLING
    // -------------------------------------------------------------

    btnStartRun.addEventListener('click', async () => {
        const supermarket = runSupermarket.value;
        const headless = runHeadless.checked;

        if (shoppingList.length === 0) {
            alert('Your shopping list is empty. Add items first!');
            return;
        }

        if (!credentialsStatus.hasUsername || !credentialsStatus.hasPassword) {
            alert('Please configure your supermarket credentials first!');
            return;
        }

        try {
            btnStartRun.disabled = true;
            const res = await fetch('/api/autobuy/run', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ supermarket, headless })
            });

            if (res.ok) {
                addConsoleLog('INFO', `Auto-Buy process launched in background (headless=${headless}).`);
                startStatusPolling();
            } else {
                const data = await res.json();
                alert('Failed to start: ' + (data.message || 'Unknown error'));
                btnStartRun.disabled = false;
            }
        } catch (err) {
            alert('Failed to trigger execution.');
            btnStartRun.disabled = false;
        }
    });

    btnCancelRun.addEventListener('click', async () => {
        if (!confirm('Are you sure you want to cancel the scraper execution?')) return;
        try {
            const res = await fetch('/api/autobuy/cancel', { method: 'POST' });
            if (res.ok) {
                addConsoleLog('WARN', 'Cancel request sent to scraper.');
            }
        } catch (err) {
            console.error(err);
        }
    });

    function startStatusPolling() {
        if (pollIntervalId) return;
        
        pollIntervalId = setInterval(pollStatus, 1000);
        pollStatus(); // Immediate first poll
    }

    function stopStatusPolling() {
        if (pollIntervalId) {
            clearInterval(pollIntervalId);
            pollIntervalId = null;
        }
    }

    async function pollStatus() {
        try {
            const res = await fetch('/api/autobuy/status');
            if (!res.ok) return;

            const status = await res.json();
            updateUIWithStatus(status);
        } catch (err) {
            console.error('Error polling status', err);
        }
    }

    function updateUIWithStatus(status) {
        // 1. Update Badge
        executionStateBadge.textContent = status.state;
        executionStateBadge.className = `status-badge state-${status.state.toLowerCase()}`;

        // 2. Render Logs
        if (status.logs && status.logs.length > 0) {
            consoleLogLines.innerHTML = '';
            status.logs.forEach(logLine => {
                const parts = logLine.split(' - ');
                const level = parts[0] || 'INFO';
                const msg = parts.slice(1).join(' - ');

                const line = document.createElement('div');
                line.className = `log-line log-${level.toLowerCase()}`;
                line.textContent = msg;
                consoleLogLines.appendChild(line);
            });
            consoleLogLines.scrollTop = consoleLogLines.scrollHeight;
        }

        // 3. Handle state transitions
        if (status.state === 'IDLE' || status.state === 'SUCCESS' || status.state === 'FAILED') {
            runSettingsPanel.style.display = 'block';
            activeRunPanel.style.display = 'none';
            btnStartRun.disabled = false;
            stopStatusPolling();
            
            // Reload mappings if just finished successfully
            if (lastState !== status.state && status.state === 'SUCCESS') {
                loadMappings();
            }
        } else {
            runSettingsPanel.style.display = 'none';
            activeRunPanel.style.display = 'block';
            
            currentTaskLabel.textContent = `Processing: ${status.state.replace('_', ' ')}`;
            if (status.currentItemQuery) {
                currentTaskDetails.textContent = `${status.currentItemQuery} (x${status.currentItemQuantity})`;
            } else {
                currentTaskDetails.textContent = 'Initializing scraper...';
            }
            
            // Ensure polling is running
            startStatusPolling();
        }

        // 4. Modals overlays based on exact state
        
        // Resolve Missing Mapping Modal
        if (status.state === 'AWAITING_MAPPING') {
            if (resolveModal.style.display !== 'flex') {
                renderResolveProducts(status.currentItemQuery, status.searchResults);
                resolveModal.style.display = 'flex';
            }
        } else {
            resolveModal.style.display = 'none';
        }

        // Cart Final Review Modal
        if (status.state === 'AWAITING_FINAL_REVIEW') {
            reviewModal.style.display = 'flex';
        } else {
            reviewModal.style.display = 'none';
        }

        lastState = status.state;
    }

    // -------------------------------------------------------------
    // RESOLVE INTERACTIVE MODAL SELECTION
    // -------------------------------------------------------------

    function renderResolveProducts(query, products) {
        resolveQueryTitle.textContent = `No mapping found for query: "${query}"`;
        resolveProductsGrid.innerHTML = '';

        if (!products || products.length === 0) {
            resolveProductsGrid.innerHTML = '<p class="empty-state-text">No products found in search results.</p>';
            return;
        }

        products.forEach(p => {
            const card = document.createElement('div');
            card.className = 'product-match-card';
            card.innerHTML = `
                <div class="pm-category">${escapeHtml(p.category || 'Product')}</div>
                <h4 class="pm-name" title="${escapeHtml(p.name)}">${escapeHtml(p.name)}</h4>
                <div class="pm-brand">Brand: ${escapeHtml(p.brand || 'N/A')}</div>
                <div class="pm-footer">
                    <span class="pm-price">${p.price.toFixed(2)} €</span>
                    <a href="${p.url}" target="_blank" class="pm-link">View Page ↗</a>
                </div>
                <button class="btn btn-primary btn-small btn-select-product" onclick="selectProductMatch('${p.externalId}')">Select & Map</button>
            `;
            resolveProductsGrid.appendChild(card);
        });
    }

    window.selectProductMatch = async (externalId) => {
        try {
            const res = await fetch('/api/autobuy/resolve', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ externalId })
            });
            if (res.ok) {
                resolveModal.style.display = 'none';
                addConsoleLog('INFO', `Mapped query to product SKU: ${externalId}`);
            } else {
                alert('Failed to resolve mapping.');
            }
        } catch (err) {
            console.error(err);
        }
    };

    btnSkipMapping.addEventListener('click', async () => {
        try {
            const res = await fetch('/api/autobuy/resolve', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ externalId: 'skip' })
            });
            if (res.ok) {
                resolveModal.style.display = 'none';
                addConsoleLog('WARN', 'Skipped mapping for current item.');
            }
        } catch (err) {
            console.error(err);
        }
    });

    // -------------------------------------------------------------
    // FINAL CART REVIEW MODAL ACTIONS
    // -------------------------------------------------------------

    btnCompleteRun.addEventListener('click', async () => {
        try {
            btnCompleteRun.disabled = true;
            btnCompleteRun.textContent = 'Closing...';
            const res = await fetch('/api/autobuy/complete', { method: 'POST' });
            if (res.ok) {
                reviewModal.style.display = 'none';
                addConsoleLog('SUCCESS', 'Execution run marked complete. Browser closed.');
            }
        } catch (err) {
            console.error(err);
        } finally {
            btnCompleteRun.disabled = false;
            btnCompleteRun.textContent = 'Complete Run & Close Browser';
        }
    });

    // Helper: Escape HTML string
    function escapeHtml(str) {
        if (!str) return '';
        return str
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }

    // Run startup
    init();
});
