// Vanilla Javascript for Supermarket Auto-Buy Dashboard

document.addEventListener('DOMContentLoaded', () => {
    // State
    let shoppingList = [];
    let allMappings = {};
    let credentialsStatus = { hasUsername: false, hasPassword: false, username: '' };
    let pollIntervalId = null;
    let lastState = 'IDLE';
    let isRefining = false;
    let lastRenderedResultsJson = '';
    let modalMode = 'resolve';
    let currentResolvingQuery = '';
    let currentMappingInstructions = '';
    let searchResultsCache = [];
    let lastStatusSearchResults = [];

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

    const exhaustedResolutionsPanel = document.getElementById('exhausted-resolutions-panel');
    const exhaustedItemsList = document.getElementById('exhausted-items-list');
    const btnCancelRunExhausted = document.getElementById('btn-cancel-run-exhausted');
    
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
    const configBackupDir = document.getElementById('config-backup-dir');
    const btnBrowseBackup = document.getElementById('btn-browse-backup');
    const btnShutdown = document.getElementById('btn-shutdown');
    const shutdownOverlay = document.getElementById('shutdown-overlay');
    
    const resolveModal = document.getElementById('resolve-modal');
    const resolveModalTitle = document.getElementById('resolve-modal-title');
    const resolveQueryTitle = document.getElementById('resolve-query-title');
    const resolveModalDesc = document.getElementById('resolve-modal-desc');
    const resolveSearchBoxWrapper = document.getElementById('resolve-search-box-wrapper');
    const resolveSearchInput = document.getElementById('resolve-search-input');
    const btnResolveSearch = document.getElementById('btn-resolve-search');
    const resolveProductsGrid = document.getElementById('resolve-products-grid');
    const btnSkipMapping = document.getElementById('btn-skip-mapping');
    const resolveRefineInput = document.getElementById('resolve-refine-input');
    const btnRefineSearch = document.getElementById('btn-refine-search');
    const resolveOriginalQuery = document.getElementById('resolve-original-query');
    const btnCloseResolve = document.getElementById('btn-close-resolve');
    
    const finalReviewPanel = document.getElementById('final-review-panel');
    const btnCompleteRunInline = document.getElementById('btn-complete-run-inline');
    const btnCompleteRunKeep = document.getElementById('btn-complete-run-keep');
    const browserRunningWarning = document.getElementById('browser-running-warning');

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

    // Update password required attribute based on username input
    const updatePasswordRequiredState = () => {
        const hasExisting = credentialsStatus.hasUsername && credentialsStatus.hasPassword;
        const usernameChanged = credUsername.value.trim() !== (credentialsStatus.username || '');

        if (hasExisting && !usernameChanged) {
            credPassword.required = false;
            credPassword.placeholder = '•••••••••••• (Leave blank to keep unchanged)';
        } else {
            credPassword.required = true;
            credPassword.placeholder = 'Password (Required)';
        }
    };

    credUsername.addEventListener('input', updatePasswordRequiredState);

    btnOpenCreds.addEventListener('click', async () => {
        credUsername.value = credentialsStatus.username || '';
        credPassword.value = '';
        updatePasswordRequiredState();

        try {
            const res = await fetch('/api/config/backup-dir');
            if (res.ok) {
                const data = await res.json();
                configBackupDir.value = data.backupDir || '';
            }
        } catch (err) {
            console.error('Failed to load backup directory config', err);
        }

        credsModal.style.display = 'flex';
    });

    btnCloseCredsModal.addEventListener('click', () => credsModal.style.display = 'none');
    btnCancelCreds.addEventListener('click', () => credsModal.style.display = 'none');

    credsForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const username = credUsername.value.trim();
        const password = credPassword.value;
        const supermarket = document.getElementById('cred-supermarket').value;
        const backupDir = configBackupDir.value.trim().replace(/\\/g, '/');
        configBackupDir.value = backupDir;

        // Determine if credentials need to be updated
        const hasExistingCreds = credentialsStatus.hasUsername && credentialsStatus.hasPassword;
        const credsUnchanged = hasExistingCreds && username === credentialsStatus.username && password === '';

        try {
            if (!credsUnchanged) {
                const credRes = await fetch('/api/credentials', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ supermarket, username, password })
                });

                if (!credRes.ok) {
                    const data = await credRes.json();
                    await showAlert('Settings Error', 'Failed to save credentials: ' + (data.message || 'Unknown error'));
                    return;
                }
            }

            const backupRes = await fetch('/api/config/backup-dir', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ backupDir })
            });

            if (backupRes.ok) {
                credsModal.style.display = 'none';
                addConsoleLog('SUCCESS', 'Settings updated successfully.');
                checkCredentialsStatus();
            } else {
                const data = await backupRes.json();
                await showAlert('Settings Error', 'Failed to save backup settings: ' + (data.message || 'Unknown error'));
            }
        } catch (err) {
            console.error('Settings submission failed:', err);
            await showAlert('Connection Error', 'Error communicating with backend settings API.');
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
                    <input type="text" class="item-name-input" value="${escapeHtml(item.query)}" onchange="updateItemName(${index}, this.value)" aria-label="Item name" />
                </div>
                <div class="row-actions">
                    <button class="btn btn-secondary btn-small" onclick="adjustItemQty(${index}, -1)">-</button>
                    <input type="number" class="item-qty-input" value="${item.quantity}" min="1" max="100" onchange="setItemQty(${index}, this.value)" aria-label="Item quantity" />
                    <button class="btn btn-secondary btn-small" onclick="adjustItemQty(${index}, 1)">+</button>
                    <button class="btn-trash" onclick="removeShoppingItem(${index})" title="Delete item">🗑️</button>
                </div>
            `;
            shoppingListItems.appendChild(row);
        });
    }

    globalThis.updateItemName = (index, newName) => {
        newName = newName.trim();
        if (!newName) {
            // Restore original name if input was cleared
            renderShoppingList();
            return;
        }
        shoppingList[index].query = newName;
        saveShoppingListToServer();
    };

    globalThis.setItemQty = (index, value) => {
        let qty = Number.parseInt(value, 10);
        if (Number.isNaN(qty) || qty < 1) {
            qty = 1;
        } else if (qty > 100) {
            qty = 100;
        }
        shoppingList[index].quantity = qty;
        renderShoppingList();
        saveShoppingListToServer();
    };

    globalThis.adjustItemQty = (index, delta) => {
        const item = shoppingList[index];
        const newQty = item.quantity + delta;
        if (newQty >= 1 && newQty <= 100) {
            shoppingList[index] = { ...item, quantity: newQty };
            renderShoppingList();
            saveShoppingListToServer();
        }
    };

    globalThis.removeShoppingItem = (index) => {
        shoppingList.splice(index, 1);
        renderShoppingList();
        saveShoppingListToServer();
    };

    addItemForm.addEventListener('submit', (e) => {
        e.preventDefault();
        const query = newItemQuery.value.trim();
        const quantity = Number.parseInt(newItemQty.value, 10);

        if (!query) return;

        // Check if item query already exists
        const existingIdx = shoppingList.findIndex(item => item.query.toLowerCase() === query.toLowerCase());
        if (existingIdx === -1) {
            shoppingList.push({ query, quantity });
        } else {
            shoppingList[existingIdx] = {
                ...shoppingList[existingIdx],
                quantity: shoppingList[existingIdx].quantity + quantity
            };
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
        mappingsListContainer.innerHTML = '';

        const keys = Object.keys(allMappings);
        let matchCount = 0;

        keys.forEach(searchText => {
            const list = allMappings[searchText];
            const matchesQuery = searchText.toLowerCase().includes(query) ||
                list.some(m => m.productName?.toLowerCase().includes(query) || m.externalProductId.toLowerCase().includes(query));

            if (!matchesQuery && query !== '') return;

            matchCount++;

            const group = document.createElement('div');
            group.className = 'mapping-group';

            let rowsHtml = '';
            list.forEach((m, index) => {
                const isFirst = index === 0;
                const isLast = index === list.length - 1;

                rowsHtml += `
                    <div class="mapping-row alt-row">
                        <div class="mapping-details">
                            <span class="mapping-badge ${m.priority === 0 ? 'primary-badge' : 'alt-badge'}">
                                ${m.priority === 0 ? 'Primary' : 'Alt ' + m.priority}
                            </span>
                            <span class="mapping-sku-name" title="${escapeHtml(m.productName || '')}">
                                SKU: ${escapeHtml(m.externalProductId)} ${m.productName ? `— ${escapeHtml(m.productName)}` : ''}
                            </span>
                        </div>
                        <div class="mapping-actions">
                            <button class="btn-arrow" onclick="promoteMapping(${m.id})" ${isFirst ? 'disabled' : ''} title="Move Up">↑</button>
                            <button class="btn-arrow" onclick="demoteMapping(${m.id})" ${isLast ? 'disabled' : ''} title="Move Down">↓</button>
                            <button class="btn-trash" onclick="deleteMapping(${m.id})" title="Delete mapping">🗑️</button>
                        </div>
                    </div>
                `;
            });

            group.innerHTML = `
                <div class="mapping-group-header">
                    <span class="mapping-query">"${escapeHtml(searchText)}"</span>
                    <button class="btn btn-secondary btn-small" onclick="openAddAlternativeModal('${escapeHtml(searchText)}')">+ Add Alternative</button>
                </div>
                <div class="mapping-group-rows">
                    ${rowsHtml}
                </div>
            `;
            mappingsListContainer.appendChild(group);
        });

        if (matchCount === 0) {
            mappingsListContainer.innerHTML = '<div class="empty-state-text">No product mappings found.</div>';
        }
    }

    globalThis.promoteMapping = async (id) => {
        try {
            const res = await fetch(`/api/mappings/${id}/promote`, { method: 'POST' });
            if (res.ok) {
                loadMappings();
            } else {
                await showAlert('Error', 'Failed to promote mapping.');
            }
        } catch (err) {
            console.error(err);
        }
    };

    globalThis.demoteMapping = async (id) => {
        try {
            const res = await fetch(`/api/mappings/${id}/demote`, { method: 'POST' });
            if (res.ok) {
                loadMappings();
            } else {
                await showAlert('Error', 'Failed to demote mapping.');
            }
        } catch (err) {
            console.error(err);
        }
    };

    globalThis.deleteMapping = async (id) => {
        if (!await showConfirm('Delete Mapping', 'Are you sure you want to delete this product mapping?', true)) return;
        try {
            const res = await fetch(`/api/mappings/${id}`, { method: 'DELETE' });
            if (res.ok) {
                addConsoleLog('INFO', `Deleted product mapping ID ${id}`);
                loadMappings();
            } else {
                await showAlert('Delete Error', 'Failed to delete mapping.');
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
            await showAlert('Empty List', 'Your shopping list is empty. Add items first!');
            return;
        }

        if (!credentialsStatus.hasUsername || !credentialsStatus.hasPassword) {
            await showAlert('Credentials Required', 'Please configure your supermarket credentials first!');
            return;
        }

        const unmappedItems = shoppingList.filter(item => {
            const queryClean = item.query.toLowerCase().trim();
            const mappingList = allMappings[queryClean] || Object.entries(allMappings).find(([k]) => k.toLowerCase().trim() === queryClean)?.[1];
            if (!mappingList || !Array.isArray(mappingList)) {
                return true;
            }
            return !mappingList.some(m => m.supermarket.toUpperCase() === supermarket.toUpperCase());
        });

        if (unmappedItems.length > 0) {
            const confirmed = await showConfirm(
                'Unmapped Items Detected',
                'Your list has unmapped items, you will be asked to select the items.\n<strong>The app will remember your selection</strong>, for the next time you want to buy the same product.'
            );
            if (!confirmed) {
                return;
            }
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
                await showAlert('Execution Error', 'Failed to start: ' + (data.message || 'Unknown error'));
                btnStartRun.disabled = false;
            }
        } catch (err) {
            console.error('Launch execution failed:', err);
            await showAlert('Execution Error', 'Failed to trigger execution.');
            btnStartRun.disabled = false;
        }
    });

    btnCancelRun.addEventListener('click', async () => {
        if (!await showConfirm('Cancel Execution', 'Are you sure you want to cancel the scraper execution?', true)) return;
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
        updateExecutionBadge(status.state);
        updateConsoleLogs(status.logs);
        handleExecutionStatePanels(status);
        handleModals(status);
        lastState = status.state;
    }

    function updateExecutionBadge(state) {
        executionStateBadge.textContent = state;
        executionStateBadge.className = `status-badge state-${state.toLowerCase()}`;
    }

    function updateConsoleLogs(logs) {
        if (logs && logs.length > 0) {
            consoleLogLines.innerHTML = '';
            logs.forEach(logLine => {
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
    }

    function handleExecutionStatePanels(status) {
        if (status.state === 'IDLE' || status.state === 'SUCCESS' || status.state === 'FAILED') {
            runSettingsPanel.style.display = 'block';
            activeRunPanel.style.display = 'none';
            exhaustedResolutionsPanel.style.display = 'none';
            finalReviewPanel.style.display = 'none';
            btnStartRun.disabled = false;
            resolveModal.style.display = 'none';
            
            if (status.browserOpen) {
                browserRunningWarning.style.display = 'block';
            } else {
                browserRunningWarning.style.display = 'none';
            }
            
            stopStatusPolling();
            
            if (lastState !== status.state && status.state === 'SUCCESS') {
                loadMappings();
            }
        } else if (status.state === 'AWAITING_EXHAUSTED_RESOLUTIONS') {
            runSettingsPanel.style.display = 'none';
            activeRunPanel.style.display = 'none';
            exhaustedResolutionsPanel.style.display = 'block';
            finalReviewPanel.style.display = 'none';
            browserRunningWarning.style.display = 'none';
            startStatusPolling();
            
            renderExhaustedItems(status.exhaustedItems, status.currentItemQuery);
            lastStatusSearchResults = status.searchResults || [];
        } else if (status.state === 'AWAITING_FINAL_REVIEW') {
            runSettingsPanel.style.display = 'none';
            activeRunPanel.style.display = 'none';
            exhaustedResolutionsPanel.style.display = 'none';
            finalReviewPanel.style.display = 'block';
            browserRunningWarning.style.display = 'none';
            startStatusPolling();
            
            renderReviewDetails(status);
        } else {
            runSettingsPanel.style.display = 'none';
            activeRunPanel.style.display = 'block';
            exhaustedResolutionsPanel.style.display = 'none';
            finalReviewPanel.style.display = 'none';
            browserRunningWarning.style.display = 'none';
            
            currentTaskLabel.textContent = `Processing: ${status.state.replace('_', ' ')}`;
            if (status.currentItemQuery) {
                currentTaskDetails.textContent = `${status.currentItemQuery} (x${status.currentItemQuantity})`;
            } else {
                currentTaskDetails.textContent = 'Initializing scraper...';
            }
            startStatusPolling();
        }
    }

    function renderExhaustedItems(items, activeQuery) {
        exhaustedItemsList.innerHTML = '';
        if (!items || items.length === 0) {
            exhaustedItemsList.innerHTML = '<div class="empty-state-text">All items resolved.</div>';
            return;
        }

        items.forEach(item => {
            const isActive = item === activeQuery;
            const row = document.createElement('div');
            row.className = 'exhausted-item-row';
            if (isActive) {
                row.style.borderColor = 'rgba(251, 191, 36, 0.4)';
                row.style.background = 'rgba(251, 191, 36, 0.05)';
            }
            row.innerHTML = `
                <span class="exhausted-item-name" style="${isActive ? 'font-weight: 600;' : ''}">${escapeHtml(item)}</span>
                <div class="exhausted-item-actions">
                    ${isActive ? `
                        <button class="btn btn-primary btn-small" onclick="openResolveExhaustedModal('${escapeHtml(item)}')">Resolve</button>
                        <button class="btn btn-secondary btn-small" onclick="resolveExhaustedSkip('${escapeHtml(item)}')">Skip</button>
                    ` : `
                        <span style="font-size: 0.75rem; color: var(--text-muted); align-self: center; padding: 0.25rem 0.5rem; background: rgba(255,255,255,0.03); border: 1px solid var(--border-glass); border-radius: var(--radius-sm);">Queued</span>
                    `}
                </div>
            `;
            exhaustedItemsList.appendChild(row);
        });
    }

    globalThis.openResolveExhaustedModal = (query) => {
        modalMode = 'exhausted';
        currentResolvingQuery = query;

        resolveModalTitle.textContent = 'Choose Product Match';
        resolveQueryTitle.textContent = `Resolve unavailable query: "${query}"`;
        resolveModalDesc.textContent = 'Search the supermarket to find an alternative product to resolve this unavailable item.';

        resolveSearchBoxWrapper.style.display = 'flex';
        resolveSearchInput.value = query;

        btnSkipMapping.style.display = 'block';
        btnCloseResolve.style.display = 'none';

        renderResolveProducts(query, lastStatusSearchResults);
        resolveModal.style.display = 'flex';
    };

    globalThis.resolveExhaustedSkip = async (query) => {
        try {
            const res = await fetch('/api/autobuy/resolve', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ externalId: 'skip', saveMapping: false })
            });
            if (res.ok) {
                addConsoleLog('WARN', `Skipped resolution for item: ${query}`);
            } else {
                await showAlert('Error', 'Failed to skip item.');
            }
        } catch (err) {
            console.error(err);
        }
    };

    function handleModals(status) {
        if (status.state === 'AWAITING_MAPPING') {
            if (isRefining) {
                isRefining = false;
                resolveRefineInput.disabled = false;
                btnRefineSearch.disabled = false;
                btnRefineSearch.textContent = 'Search';
            }

            const resultsJson = JSON.stringify(status.searchResults);
            const instructions = status.mappingInstructions || '';
            const prevInstructions = resolveModalDesc.getAttribute('data-instructions') || '';

            if (resolveModal.style.display !== 'flex' || resultsJson !== lastRenderedResultsJson || instructions !== prevInstructions) {
                if (resolveModal.style.display !== 'flex') {
                    resolveRefineInput.value = '';
                }
                modalMode = 'resolve';
                currentResolvingQuery = status.currentItemQuery;
                resolveModalTitle.textContent = 'Choose Product Match';
                resolveQueryTitle.textContent = `No mapping found for query: "${status.currentItemQuery}"`;
                
                resolveModalDesc.setAttribute('data-instructions', instructions);
                if (instructions) {
                    resolveModalDesc.innerHTML = `<span style="color: #f87171; font-weight: 600;">⚠️ ${escapeHtml(instructions)}</span>`;
                } else {
                    resolveModalDesc.textContent = 'Playwright searched the store and found the following top results. Please choose the correct product to map it permanently and continue the run.';
                }

                btnSkipMapping.style.display = 'block';
                btnCloseResolve.style.display = 'none';
                renderResolveProducts(status.currentItemQuery, status.searchResults);
                resolveModal.style.display = 'flex';
                lastRenderedResultsJson = resultsJson;
            }
        } else if (status.state === 'AWAITING_EXHAUSTED_RESOLUTIONS') {
            // Handled manually via resolution panel buttons
        } else {
            if (modalMode !== 'alternative') {
                if (!isRefining || (status.state !== 'RUNNING' && status.state !== 'AWAITING_MAPPING')) {
                    resolveModal.style.display = 'none';
                    lastRenderedResultsJson = '';
                    isRefining = false;
                    resolveRefineInput.disabled = false;
                    btnRefineSearch.disabled = false;
                    btnRefineSearch.textContent = 'Search';
                }
            }
        }
    }

    function renderReviewDetails(status) {
        if (status.skippedItems && status.skippedItems.length > 0) {
            let skippedListHtml = status.skippedItems.map(item => `<li>${escapeHtml(item)}</li>`).join('');
            finalReviewDetails.innerHTML = `
                <p class="task-label" style="color: var(--color-danger); font-weight: 600;">⚠️ RUN COMPLETED WITH SKIPPED ITEMS</p>
                <h3 class="task-details" style="font-size: 1.05rem; margin-top: 0.25rem;">Review Cart & Skipped Items</h3>
                <p style="font-size: 0.75rem; color: var(--text-muted); margin-top: 0.25rem; line-height: 1.4;">Some items could not be added because they are unavailable:</p>
                <div style="text-align: left; max-height: 100px; overflow-y: auto; background: rgba(244, 63, 94, 0.05); border: 1px solid rgba(244, 63, 94, 0.2); border-radius: var(--radius-sm); padding: 0.5rem 0.75rem; margin: 0.5rem 0;">
                    <ul style="margin: 0; padding-left: 1.25rem; color: var(--text-main); font-size: 0.8125rem; line-height: 1.4;">
                        ${skippedListHtml}
                    </ul>
                </div>
                <p style="font-size: 0.75rem; color: var(--text-muted); line-height: 1.4;">Please review your shopping cart in the opened browser window. You can complete the checkout manually if desired.</p>
            `;
        } else {
            finalReviewDetails.innerHTML = `
                <p class="task-label" style="color: var(--color-accent); font-weight: 600;">🎉 RUN COMPLETED</p>
                <h3 class="task-details" style="font-size: 1.05rem; margin-top: 0.25rem;">Awaiting Cart Verification</h3>
                <p style="font-size: 0.75rem; color: var(--text-muted); margin-top: 0.25rem; line-height: 1.4;">All items successfully added to cart! Please review your shopping cart in the opened browser window. You can complete the checkout manually if desired.</p>
            `;
        }
    }

    // -------------------------------------------------------------
    // RESOLVE INTERACTIVE MODAL SELECTION
    // -------------------------------------------------------------

    function renderResolveProducts(query, products) {
        resolveQueryTitle.textContent = modalMode === 'alternative' ? `Alternative mapping for: "${query}"` : `Choose Product Match for: "${query}"`;
        if (resolveOriginalQuery) {
            resolveOriginalQuery.textContent = `"${query}"`;
        }
        resolveProductsGrid.innerHTML = '';
        searchResultsCache = products || [];

        if (!products || products.length === 0) {
            resolveProductsGrid.innerHTML = '<p class="empty-state-text">No products found in search results.</p>';
            return;
        }

        products.forEach(p => {
            const isOutOfStock = p.available === false;
            const card = document.createElement('div');
            card.className = 'product-match-card';
            if (isOutOfStock) {
                card.style.opacity = '0.75';
            }
            card.innerHTML = `
                <div class="pm-category" style="display: flex; justify-content: space-between; align-items: center;">
                    <span>${escapeHtml(p.category || 'Product')}</span>
                    ${isOutOfStock ? `<span style="background: rgba(239, 68, 68, 0.15); color: #f87171; border: 1px solid rgba(239, 68, 68, 0.3); font-size: 0.7rem; padding: 0.1rem 0.4rem; border-radius: var(--radius-sm); font-weight: 600; text-transform: uppercase; letter-spacing: 0.03em;">Out of Stock</span>` : ''}
                </div>
                <h4 class="pm-name" title="${escapeHtml(p.name)}">${escapeHtml(p.name)}</h4>
                <div class="pm-brand">Brand: ${escapeHtml(p.brand || 'N/A')}</div>
                <div class="pm-footer">
                    <span class="pm-price">${p.price.toFixed(2)} €</span>
                    <a href="${p.url}" target="_blank" class="pm-link">View Page ↗</a>
                </div>
                <div class="pm-buttons-container">
                    <button class="btn btn-primary btn-small btn-select-product" onclick="selectProductMatch('${p.externalId}', true)" title="Select and save mapping to database" ${isOutOfStock ? 'disabled style="opacity: 0.5; cursor: not-allowed;"' : ''}>Select & Save</button>
                    <button class="btn btn-secondary btn-small btn-select-product" onclick="selectProductMatch('${p.externalId}', false)" title="Select only for this run" ${isOutOfStock ? 'disabled style="opacity: 0.5; cursor: not-allowed;"' : ''}>Select Only</button>
                </div>
            `;
            resolveProductsGrid.appendChild(card);
        });
    }

    globalThis.openAddAlternativeModal = (searchText) => {
        modalMode = 'alternative';
        currentResolvingQuery = searchText;
        
        resolveModalTitle.textContent = 'Add Alternative Product';
        resolveQueryTitle.textContent = `Alternative mapping for: "${searchText}"`;
        resolveModalDesc.textContent = 'Search the supermarket to find an alternative product. Selecting a product will add it to the mapping chain.';
        
        resolveSearchBoxWrapper.style.display = 'flex';
        resolveSearchInput.value = searchText;
        
        btnSkipMapping.style.display = 'none';
        btnCloseResolve.style.display = 'block';
        
        resolveProductsGrid.innerHTML = '<div class="empty-state-text">Enter search query and click search to find alternative products.</div>';
        resolveModal.style.display = 'flex';
    };

    globalThis.selectProductMatch = async (externalId, saveMapping = true) => {
        const selectedProd = searchResultsCache.find(p => p.externalId === externalId);
        const isOutOfStock = selectedProd && selectedProd.available === false;

        if (isOutOfStock && modalMode === 'exhausted') {
            await showAlert('Product Unavailable', 'This product is out of stock. Please select an available alternative to complete the purchase for this run.');
            return;
        }

        if (isOutOfStock && !saveMapping) {
            await showAlert('Product Unavailable', 'This product is out of stock. Please select an in-stock product or use "Select & Save" to save it as a mapping.');
            return;
        }

        const buttons = resolveProductsGrid.querySelectorAll('.btn-select-product');
        buttons.forEach(btn => btn.disabled = true);
        const clickedBtn = Array.from(buttons).find(btn => {
            const clickAttr = btn.getAttribute('onclick') || '';
            return clickAttr.includes(`'${externalId}'`) && clickAttr.includes(String(saveMapping));
        });
        let originalText = '';
        if (clickedBtn) {
            originalText = clickedBtn.textContent;
            clickedBtn.textContent = 'Verifying...';
        }

        try {
            let res;
            if (modalMode === 'alternative') {
                res = await fetch('/api/autobuy/add-alternative', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        searchText: currentResolvingQuery,
                        supermarket: runSupermarket.value || 'CONTINENTE',
                        externalId: externalId,
                        productName: searchResultsCache.find(p => p.externalId === externalId)?.name || ''
                    })
                });
            } else {
                res = await fetch('/api/autobuy/resolve', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ externalId, saveMapping })
                });
            }

            if (res.ok) {
                if (modalMode === 'alternative') {
                    await res.json().catch(() => ({}));
                    resolveModal.style.display = 'none';
                    addConsoleLog('INFO', `Added alternative product SKU: ${externalId}`);
                    loadMappings();
                } else {
                    const data = await res.json().catch(() => ({}));
                    if (data.added) {
                        resolveModal.style.display = 'none';
                        addConsoleLog('INFO', `Resolved item to product SKU: ${externalId} (Save: ${saveMapping})`);
                    } else {
                        addConsoleLog('WARN', `Saved mapping for SKU: ${externalId}, but it was out of stock.`);
                        if (selectedProd) {
                            selectedProd.available = false;
                        }
                        
                        resolveModalDesc.innerHTML = `<span style="color: #f87171; font-weight: 600;">⚠️ ${escapeHtml(data.message || 'Saved as mapping, but out of stock. Please select a fallback alternative.')}</span>`;
                        renderResolveProducts(currentResolvingQuery, searchResultsCache);
                    }
                }
            } else {
                const data = await res.json().catch(() => ({}));
                const errorMsg = data.message || 'Failed to resolve/add mapping.';
                await showAlert('Product Unavailable', errorMsg);
                if (selectedProd) {
                    selectedProd.available = false;
                }
                renderResolveProducts(currentResolvingQuery, searchResultsCache);
            }
        } catch (err) {
            console.error(err);
            await showAlert('Error', 'An unexpected error occurred.');
        } finally {
            buttons.forEach(btn => btn.disabled = false);
            if (clickedBtn) {
                clickedBtn.textContent = originalText;
            }
        }
    };

    btnSkipMapping.addEventListener('click', async () => {
        try {
            const res = await fetch('/api/autobuy/resolve', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ externalId: 'skip', saveMapping: false })
            });
            if (res.ok) {
                resolveModal.style.display = 'none';
                addConsoleLog('WARN', 'Skipped mapping for current item.');
            }
        } catch (err) {
            console.error(err);
        }
    });

    async function triggerSearchRefinement() {
        const query = resolveRefineInput.value.trim();
        if (!query) return;

        isRefining = true;
        resolveRefineInput.disabled = true;
        btnRefineSearch.disabled = true;
        btnRefineSearch.textContent = 'Searching...';

        // Show inline loader in the products grid
        resolveProductsGrid.innerHTML = `
            <div style="grid-column: 1 / -1; display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 3rem 1rem; gap: 1rem; color: var(--text-muted);">
                <div class="spinner"></div>
                <p style="font-size: 0.9375rem; color: var(--text-muted);">Searching store for "${escapeHtml(query)}"...</p>
            </div>
        `;

        try {
            const res = await fetch('/api/autobuy/refine', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ query })
            });

            if (!res.ok) {
                const errData = await res.json().catch(() => ({}));
                await showAlert('Refinement Error', errData.message || 'Failed to trigger search refinement.');
                isRefining = false;
                resolveRefineInput.disabled = false;
                btnRefineSearch.disabled = false;
                btnRefineSearch.textContent = 'Search';
                lastRenderedResultsJson = '';
                pollStatus();
            }
        } catch (err) {
            console.error(err);
            isRefining = false;
            resolveRefineInput.disabled = false;
            btnRefineSearch.disabled = false;
            btnRefineSearch.textContent = 'Search';
            lastRenderedResultsJson = '';
            pollStatus();
        }
    }

    btnRefineSearch.addEventListener('click', triggerSearchRefinement);
    resolveRefineInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') {
            e.preventDefault();
            triggerSearchRefinement();
        }
    });

    btnCloseResolve.addEventListener('click', () => {
        resolveModal.style.display = 'none';
    });

    btnCancelRunExhausted.addEventListener('click', async () => {
        if (!await showConfirm('Cancel Execution', 'Are you sure you want to cancel the scraper execution?', true)) return;
        try {
            const res = await fetch('/api/autobuy/cancel', { method: 'POST' });
            if (res.ok) {
                addConsoleLog('WARN', 'Cancel request sent to scraper.');
            }
        } catch (err) {
            console.error(err);
        }
    });
    // -------------------------------------------------------------
    // FINAL CART REVIEW MODAL ACTIONS
    // -------------------------------------------------------------

    btnCompleteRunInline.addEventListener('click', async () => {
        try {
            btnCompleteRunInline.disabled = true;
            btnCompleteRunInline.textContent = 'Closing...';
            const res = await fetch('/api/autobuy/complete?keepBrowser=false', { method: 'POST' });
            if (res.ok) {
                addConsoleLog('SUCCESS', 'Execution run marked complete. Browser closed.');
            }
        } catch (err) {
            console.error(err);
        } finally {
            btnCompleteRunInline.disabled = false;
            btnCompleteRunInline.textContent = 'Finish & Close Browser';
        }
    });

    btnCompleteRunKeep.addEventListener('click', async () => {
        try {
            btnCompleteRunKeep.disabled = true;
            btnCompleteRunKeep.textContent = 'Finishing...';
            const res = await fetch('/api/autobuy/complete?keepBrowser=true', { method: 'POST' });
            if (res.ok) {
                addConsoleLog('SUCCESS', 'Execution run marked complete. Browser kept open.');
            }
        } catch (err) {
            console.error(err);
        } finally {
            btnCompleteRunKeep.disabled = false;
            btnCompleteRunKeep.textContent = 'Finish (Keep Browser Open)';
        }
    });

    // Shutdown and Backup Action
    if (btnShutdown) {
        btnShutdown.addEventListener('click', async () => {
            try {
                const statusRes = await fetch('/api/autobuy/backup-status');
                let proceed = true;
                let isConfigured = false;

                if (statusRes.ok) {
                    const statusData = await statusRes.json();
                    isConfigured = statusData.isConfigured;
                    if (isConfigured) {
                        proceed = await showConfirm(
                            "Shutdown Application",
                            "Are you sure you want to shut down the application?\n\n" +
                            "This will save a database backup to:\n" + statusData.backupDir + "\n\n" +
                            "and stop the application server."
                        );
                    } else {
                        proceed = await showConfirm(
                            "Shutdown Application",
                            "Are you sure you want to shut down the application?\n\n" +
                            "Warning: No backup directory is configured. The database backup will be SKIPPED.\n\n" +
                            "Do you want to proceed with shutdown anyway?",
                            true
                        );
                    }
                } else {
                    proceed = await showConfirm(
                        "Shutdown Application",
                        "Are you sure you want to shut down the application and close the server?",
                        true
                    );
                }

                if (!proceed) {
                    return;
                }

                btnShutdown.disabled = true;
                const shutdownRes = await fetch('/api/shutdown', { method: 'POST' });

                if (shutdownRes.ok) {
                    const shutdownMessage = document.getElementById('shutdown-message');
                    if (shutdownMessage) {
                        if (isConfigured) {
                            shutdownMessage.textContent = "The application has been shut down gracefully and the database backup has been created.";
                        } else {
                            shutdownMessage.textContent = "The application has been shut down gracefully.";
                        }
                    }
                    shutdownOverlay.style.display = 'flex';
                    if (pollIntervalId) {
                        clearInterval(pollIntervalId);
                    }
                } else {
                    await showAlert('Shutdown Error', "Failed to initiate shutdown.");
                    btnShutdown.disabled = false;
                }
            } catch (err) {
                console.error("Shutdown failed:", err);
                await showAlert('Shutdown Error', "Error communicating with shutdown API.");
            }
        });
    }

    if (btnBrowseBackup) {
        btnBrowseBackup.addEventListener('click', async () => {
            btnBrowseBackup.disabled = true;
            btnBrowseBackup.textContent = 'Opening...';
            
            try {
                const res = await fetch('/api/config/select-native-dir', { method: 'POST' });
                const data = await res.json();
                if (res.ok && data.success) {
                    configBackupDir.value = data.path;
                    addConsoleLog('SUCCESS', `Selected database backup directory: ${data.path}`);
                } else if (data.message !== 'Selection cancelled') {
                    await showAlert('Directory Error', 'Failed to select folder: ' + (data.message || 'Unknown error'));
                }
            } catch (err) {
                console.error('Failed to open native folder picker', err);
                await showAlert('Directory Error', 'Error communicating with native directory picker.');
            } finally {
                btnBrowseBackup.disabled = false;
                btnBrowseBackup.textContent = 'Browse...';
            }
        });
    }

    // -------------------------------------------------------------
    // CUSTOM IN-APP DIALOG UTILITIES
    // -------------------------------------------------------------

    function showAlert(title, message) {
        return new Promise((resolve) => {
            const modal = document.getElementById('custom-dialog-modal');
            const titleEl = document.getElementById('dialog-title');
            const messageEl = document.getElementById('dialog-message');
            const cancelBtn = document.getElementById('btn-dialog-cancel');
            const closeBtn = document.getElementById('btn-close-dialog-modal');
            const okBtn = document.getElementById('btn-dialog-ok');

            titleEl.textContent = title;
            messageEl.innerHTML = message.replaceAll('\n', '<br>');
            
            cancelBtn.style.display = 'none'; // Alerts do not have a cancel option
            okBtn.className = 'btn btn-primary';
            okBtn.textContent = 'OK';

            const cleanup = () => {
                modal.style.display = 'none';
                okBtn.removeEventListener('click', onClose);
                closeBtn.removeEventListener('click', onClose);
            };

            const onClose = () => {
                cleanup();
                resolve();
            };

            okBtn.addEventListener('click', onClose);
            closeBtn.addEventListener('click', onClose);

            modal.style.display = 'flex';
        });
    }

    function showConfirm(title, message, isDanger = false) {
        return new Promise((resolve) => {
            const modal = document.getElementById('custom-dialog-modal');
            const titleEl = document.getElementById('dialog-title');
            const messageEl = document.getElementById('dialog-message');
            const cancelBtn = document.getElementById('btn-dialog-cancel');
            const closeBtn = document.getElementById('btn-close-dialog-modal');
            const okBtn = document.getElementById('btn-dialog-ok');

            titleEl.textContent = title;
            messageEl.innerHTML = message.replaceAll('\n', '<br>');
            
            cancelBtn.style.display = 'inline-flex';
            cancelBtn.textContent = 'Cancel';
            
            okBtn.textContent = 'Confirm';
            okBtn.className = isDanger ? 'btn btn-danger' : 'btn btn-primary';

            const cleanup = () => {
                modal.style.display = 'none';
                okBtn.removeEventListener('click', onOk);
                cancelBtn.removeEventListener('click', onCancel);
                closeBtn.removeEventListener('click', onCancel);
            };

            const onOk = () => {
                cleanup();
                resolve(true);
            };

            const onCancel = () => {
                cleanup();
                resolve(false);
            };

            okBtn.addEventListener('click', onOk);
            cancelBtn.addEventListener('click', onCancel);
            closeBtn.addEventListener('click', onCancel);

            modal.style.display = 'flex';
        });
    }

    // Run startup
    init();
});

// Helper: Escape HTML string
function escapeHtml(str) {
    if (!str) return '';
    return str
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&#039;');
}
