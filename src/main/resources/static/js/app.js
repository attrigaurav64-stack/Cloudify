/**
 * Cloudify Web &bull; Professional WhatsApp-Style Real-time Chat Engine
 * Light/Dark Themes, Edit & Delete Messages, Zero Duplicate Bubbles.
 */

(function () {
  'use strict';

  // State
  let currentUser = localStorage.getItem('cloudify_user') || '';
  let activeChatId = localStorage.getItem('cloudify_chat') || 'general-chat';
  let currentTheme = localStorage.getItem('cloudify_theme') || 'dark';
  let isSoundEnabled = localStorage.getItem('cloudify_sound') !== 'false';

  let lastMessageId = 0;
  let allMessages = [];
  let renderedMessageIds = new Set();
  let pendingTempIds = new Map(); // tempId -> element
  let isAtBottom = true;

  // Editing state
  let editingMessageId = null;
  let editingOriginalContent = '';

  // Context Menu state
  let activeMenuMessageId = null;
  let activeMenuMessageContent = '';
  let activeMenuIsMe = false;

  let pollInterval = null;
  let statsInterval = null;
  let stompClient = null;

  // Conversations Catalog
  const CHATS = {
    'general-chat': {
      id: 'general-chat',
      name: 'General Chat',
      type: 'group',
      icon: 'fa-users',
      avatarClass: 'group-avatar',
      status: 'online'
    },
    'dev-discussions': {
      id: 'dev-discussions',
      name: 'Dev Discussions',
      type: 'group',
      icon: 'fa-code',
      avatarClass: 'dev-avatar',
      status: 'active'
    },
    'announcements': {
      id: 'announcements',
      name: 'Announcements',
      type: 'group',
      icon: 'fa-bullhorn',
      avatarClass: 'announce-avatar',
      status: 'channel'
    }
  };

  const USER_COLORS = [
    '#25d366', '#34b7f1', '#e542a3', '#00a884',
    '#ff9f43', '#54a0ff', '#5f27cd', '#ff6b6b'
  ];

  function getUserColor(name) {
    if (!name) return USER_COLORS[0];
    let hash = 0;
    for (let i = 0; i < name.length; i++) {
      hash = name.charCodeAt(i) + ((hash << 5) - hash);
    }
    return USER_COLORS[Math.abs(hash) % USER_COLORS.length];
  }

  function getInitials(name) {
    if (!name) return '?';
    const parts = name.trim().split(/\s+/);
    if (parts.length === 1) return parts[0].substring(0, 2).toUpperCase();
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
  }

  // Audio Chime
  function playNotificationChime() {
    if (!isSoundEnabled) return;
    try {
      const AudioCtx = window.AudioContext || window.webkitAudioContext;
      if (!AudioCtx) return;
      const ctx = new AudioCtx();
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();

      osc.type = 'sine';
      osc.frequency.setValueAtTime(659.25, ctx.currentTime);
      osc.frequency.exponentialRampToValueAtTime(880, ctx.currentTime + 0.1);

      gain.gain.setValueAtTime(0.08, ctx.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.22);

      osc.connect(gain);
      gain.connect(ctx.destination);
      osc.start();
      osc.stop(ctx.currentTime + 0.22);
    } catch (e) {}
  }

  // Toast
  let toastTimer = null;
  function showToast(text, duration = 2500) {
    const toast = document.getElementById('waToast');
    if (!toast) return;
    if (toastTimer) clearTimeout(toastTimer);
    toast.textContent = text;
    toast.classList.remove('hidden');
    toastTimer = setTimeout(() => {
      toast.classList.add('hidden');
    }, duration);
  }

  function escapeHtml(str) {
    if (!str) return '';
    return str
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }

  // Theme Management (Light & Dark)
  function applyTheme(theme) {
    currentTheme = theme;
    localStorage.setItem('cloudify_theme', currentTheme);
    document.documentElement.classList.toggle('dark', currentTheme === 'dark');
    document.body.classList.toggle('dark', currentTheme === 'dark');

    const themeIcon = document.getElementById('themeIcon');
    if (themeIcon) {
      themeIcon.className = currentTheme === 'light' ? 'fa-solid fa-moon' : 'fa-solid fa-sun';
    }
  }

  function initTheme() {
    applyTheme(currentTheme);
    const themeBtn = document.getElementById('themeToggleBtn');
    if (themeBtn) {
      themeBtn.addEventListener('click', () => {
        applyTheme(currentTheme === 'light' ? 'dark' : 'light');
        showToast(currentTheme === 'light' ? 'Light mode enabled' : 'Dark mode enabled');
      });
    }
  }

  // Profile Management
  function applyUser(name) {
    currentUser = name.trim();
    localStorage.setItem('cloudify_user', currentUser);

    const railAvatar = document.getElementById('railAvatar');
    const modalBigAvatar = document.getElementById('modalBigAvatar');
    const initials = getInitials(currentUser);

    if (railAvatar) railAvatar.textContent = initials;
    if (modalBigAvatar) modalBigAvatar.textContent = initials;

    // Refresh view bubbles for "Me" alignment
    document.querySelectorAll('.wa-message-row').forEach(row => {
      const sender = row.dataset.sender || '';
      const isMe = sender.toLowerCase() === currentUser.toLowerCase();
      if (isMe) {
        row.classList.remove('is-other');
        row.classList.add('is-me');
      } else if (!row.classList.contains('is-system')) {
        row.classList.remove('is-me');
        row.classList.add('is-other');
      }
    });
  }

  function checkUserSetup() {
    if (!currentUser) {
      const modal = document.getElementById('profileModal');
      if (modal) modal.classList.remove('hidden');
      const input = document.getElementById('profileNameInput');
      if (input) input.focus();
    } else {
      applyUser(currentUser);
    }
  }

  // Channel parser
  function parseMessageChannel(content) {
    if (!content) return { channel: 'general-chat', cleanText: '' };
    const match = content.match(/^\[([a-zA-Z0-9_-]+)\]\s*(.*)$/);
    if (match && CHATS[match[1]]) {
      return { channel: match[1], cleanText: match[2] };
    }
    return { channel: 'general-chat', cleanText: content };
  }

  // Switch Active Chat
  function switchChat(chatId, contactName) {
    activeChatId = chatId;
    localStorage.setItem('cloudify_chat', activeChatId);

    document.querySelectorAll('.chat-list-item').forEach(item => {
      if (item.dataset.chatId === chatId) {
        item.classList.add('active');
        const badge = item.querySelector('.item-badge');
        if (badge) badge.classList.add('hidden');
      } else {
        item.classList.remove('active');
      }
    });

    const chatNameEl = document.getElementById('currentChatName');
    const chatStatusEl = document.getElementById('currentChatStatus');
    const chatAvatarEl = document.getElementById('currentChatAvatar');

    if (CHATS[chatId]) {
      const meta = CHATS[chatId];
      if (chatNameEl) chatNameEl.textContent = meta.name;
      if (chatStatusEl) chatStatusEl.textContent = meta.status;
      if (chatAvatarEl) {
        chatAvatarEl.className = `contact-avatar ${meta.avatarClass}`;
        chatAvatarEl.innerHTML = `<i class="fa-solid ${meta.icon}"></i>`;
      }
    } else {
      const displayName = contactName || chatId;
      if (chatNameEl) chatNameEl.textContent = displayName;
      if (chatStatusEl) chatStatusEl.textContent = 'online';
      if (chatAvatarEl) {
        chatAvatarEl.className = 'contact-avatar';
        chatAvatarEl.style.background = getUserColor(displayName);
        chatAvatarEl.textContent = getInitials(displayName);
      }
    }

    cancelEditing();
    filterBubblesByChat();
    scrollToBottom();

    const chatView = document.getElementById('chatView');
    if (chatView) chatView.classList.add('open-mobile');
  }

  function filterBubblesByChat() {
    const rows = document.querySelectorAll('.wa-message-row');
    rows.forEach(row => {
      const msgChannel = row.dataset.channel || 'general-chat';
      const isMatch = (activeChatId === 'general-chat')
        ? (msgChannel === 'general-chat')
        : (msgChannel === activeChatId);

      row.style.display = isMatch ? 'flex' : 'none';
    });
  }

  // Context Menu Setup
  function initContextMenu() {
    const menu = document.getElementById('bubbleContextMenu');
    const actionEdit = document.getElementById('menuActionEdit');
    const actionCopy = document.getElementById('menuActionCopy');
    const actionDelete = document.getElementById('menuActionDelete');
    const menuDivider = document.getElementById('menuDividerDelete');

    document.addEventListener('click', (e) => {
      if (menu && !menu.contains(e.target) && !e.target.closest('.bubble-dropdown-btn')) {
        menu.classList.add('hidden');
      }
    });

    if (actionCopy) {
      actionCopy.addEventListener('click', () => {
        if (activeMenuMessageContent) {
          navigator.clipboard.writeText(activeMenuMessageContent).then(() => {
            showToast('Copied to clipboard');
          });
        }
        menu.classList.add('hidden');
      });
    }

    if (actionEdit) {
      actionEdit.addEventListener('click', () => {
        if (activeMenuMessageId && activeMenuIsMe) {
          startEditing(activeMenuMessageId, activeMenuMessageContent);
        }
        menu.classList.add('hidden');
      });
    }

    if (actionDelete) {
      actionDelete.addEventListener('click', () => {
        if (activeMenuMessageId && confirm('Delete this message for everyone?')) {
          deleteMessage(activeMenuMessageId);
        }
        menu.classList.add('hidden');
      });
    }
  }

  function openContextMenu(e, msgId, cleanText, isMe) {
    e.stopPropagation();
    const menu = document.getElementById('bubbleContextMenu');
    const actionEdit = document.getElementById('menuActionEdit');
    const actionDelete = document.getElementById('menuActionDelete');
    const menuDivider = document.getElementById('menuDividerDelete');

    activeMenuMessageId = msgId;
    activeMenuMessageContent = cleanText;
    activeMenuIsMe = isMe;

    // Only allow editing and deleting on your own messages
    if (actionEdit) actionEdit.style.display = isMe ? 'flex' : 'none';
    if (actionDelete) actionDelete.style.display = isMe ? 'flex' : 'none';
    if (menuDivider) menuDivider.style.display = isMe ? 'block' : 'none';

    menu.classList.remove('hidden');

    // Position menu near click
    const rect = e.target.closest('.bubble-dropdown-btn').getBoundingClientRect();
    let left = rect.left - 130;
    let top = rect.bottom + 4;

    if (left < 10) left = 10;
    if (top + 140 > window.innerHeight) top = rect.top - 140;

    menu.style.left = left + 'px';
    menu.style.top = top + 'px';
  }

  // Editing Workflow
  function startEditing(id, text) {
    editingMessageId = id;
    editingOriginalContent = text;

    const banner = document.getElementById('editBanner');
    const snippet = document.getElementById('editingSnippet');
    const input = document.getElementById('composerInput');

    if (snippet) snippet.textContent = text.length > 35 ? text.substring(0, 35) + '...' : text;
    if (banner) banner.classList.remove('hidden');
    if (input) {
      input.value = text;
      input.focus();
    }
    const sendBtn = document.getElementById('composerSendBtn');
    if (sendBtn) sendBtn.classList.add('can-send');
  }

  function cancelEditing() {
    editingMessageId = null;
    editingOriginalContent = '';
    const banner = document.getElementById('editBanner');
    if (banner) banner.classList.add('hidden');
    const input = document.getElementById('composerInput');
    if (input && input.value === editingOriginalContent) {
      input.value = '';
    }
  }

  async function saveEditedMessage(id, newContent) {
    const formatted = (activeChatId !== 'general-chat')
      ? `[${activeChatId}] ${newContent}`
      : newContent;

    try {
      const res = await fetch(`/api/messages/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ content: formatted })
      });

      // Update in DOM
      const row = document.querySelector(`.wa-message-row[data-id="${id}"]`);
      if (row) {
        row.dataset.content = newContent.toLowerCase();
        const contentEl = row.querySelector('.wa-bubble-content');
        if (contentEl) contentEl.textContent = newContent;

        const meta = row.querySelector('.wa-bubble-meta');
        if (meta && !meta.querySelector('.wa-edited-tag')) {
          const editedTag = document.createElement('span');
          editedTag.className = 'wa-edited-tag';
          editedTag.textContent = '(edited)';
          meta.insertBefore(editedTag, meta.firstChild);
        }
      }

      // Update in memory
      const m = allMessages.find(x => x.id === id);
      if (m) m.content = formatted;

      showToast('Message edited');
    } catch (e) {
      showToast('Could not save edit');
    } finally {
      cancelEditing();
    }
  }

  // Deletion Workflow
  async function deleteMessage(id) {
    try {
      await fetch(`/api/messages/${id}`, { method: 'DELETE' });

      // Animate and remove from DOM
      const row = document.querySelector(`.wa-message-row[data-id="${id}"]`);
      if (row) {
        row.style.transition = 'all 0.2s ease';
        row.style.opacity = '0';
        row.style.transform = 'scale(0.85)';
        setTimeout(() => row.remove(), 200);
      }

      renderedMessageIds.delete(id);
      allMessages = allMessages.filter(m => m.id !== id);

      showToast('Message deleted');
    } catch (e) {
      showToast('Could not delete message');
    }
  }

  // Create WhatsApp-Style Message Bubble (Zero Duplication)
  function createMessageElement(msg, tempId = null) {
    const isMe = currentUser && msg.sender && (msg.sender.toLowerCase() === currentUser.toLowerCase());
    const isSystem = msg.type === 'JOIN' || msg.type === 'LEAVE' || (msg.sender && msg.sender.toLowerCase() === 'system');

    const parsed = parseMessageChannel(msg.content);
    const text = parsed.cleanText;
    const channel = parsed.channel;

    const row = document.createElement('div');
    row.className = 'wa-message-row ' + (isSystem ? 'is-system' : (isMe ? 'is-me' : 'is-other'));

    if (msg.id) {
      row.dataset.id = msg.id;
    }
    if (tempId) {
      row.dataset.tempId = tempId;
    }

    row.dataset.sender = (msg.sender || '').toLowerCase();
    row.dataset.content = (text || '').toLowerCase();
    row.dataset.channel = channel;

    if (isSystem) {
      row.innerHTML = `
        <div class="date-divider-pill">
          <span>${escapeHtml(text)}</span>
        </div>
      `;
      return row;
    }

    const time = msg.formattedTime || 'Now';
    const senderColor = getUserColor(msg.sender);

    row.innerHTML = `
      <div class="wa-bubble">
        ${!isMe ? `<span class="wa-sender-title" style="color: ${senderColor}">${escapeHtml(msg.sender)}</span>` : ''}
        <span class="wa-bubble-content">${escapeHtml(text)}</span>
        <div class="wa-bubble-meta">
          <span class="wa-time">${escapeHtml(time)}</span>
          ${isMe ? `<span class="wa-tick"><i class="fa-solid fa-check-double"></i></span>` : ''}
        </div>
        <button type="button" class="bubble-dropdown-btn" title="Options">
          <i class="fa-solid fa-chevron-down"></i>
        </button>
      </div>
    `;

    // Dropdown options trigger
    const dropdownBtn = row.querySelector('.bubble-dropdown-btn');
    if (dropdownBtn) {
      dropdownBtn.addEventListener('click', (e) => {
        const id = row.dataset.id ? parseInt(row.dataset.id, 10) : null;
        openContextMenu(e, id, text, isMe);
      });
    }

    return row;
  }

  // Append new messages to feed with STRICT DEDUPLICATION
  function appendMessages(newMessages, isInitial = false) {
    if (!Array.isArray(newMessages) || newMessages.length === 0) return;

    const feed = document.getElementById('bubblesFeed');
    const loading = document.getElementById('flowLoading');
    let addedCount = 0;
    let hasOtherUserMessage = false;

    newMessages.forEach(msg => {
      const msgId = msg.id;
      if (!msgId) return;

      // 1. Check if already known
      if (renderedMessageIds.has(msgId)) return;

      // 2. Check if DOM already has this ID
      if (document.querySelector(`.wa-message-row[data-id="${msgId}"]`)) {
        renderedMessageIds.add(msgId);
        lastMessageId = Math.max(lastMessageId, msgId);
        return;
      }

      // 3. Check if matching an optimistic pending bubble
      const parsed = parseMessageChannel(msg.content);
      const pendingRow = document.querySelector(`.wa-message-row[data-temp-id]`);
      if (pendingRow && pendingRow.dataset.sender === (msg.sender || '').toLowerCase()
          && pendingRow.dataset.content === parsed.cleanText.toLowerCase()) {
        pendingRow.dataset.id = msgId;
        delete pendingRow.dataset.tempId;
        const timeEl = pendingRow.querySelector('.wa-time');
        if (timeEl && msg.formattedTime) timeEl.textContent = msg.formattedTime;
        renderedMessageIds.add(msgId);
        lastMessageId = Math.max(lastMessageId, msgId);
        allMessages.push(msg);
        return;
      }

      // Otherwise append brand new bubble
      renderedMessageIds.add(msgId);
      lastMessageId = Math.max(lastMessageId, msgId);
      allMessages.push(msg);

      const el = createMessageElement(msg);
      if (feed) feed.appendChild(el);
      addedCount++;

      if (!isInitial && currentUser && msg.sender && msg.sender.toLowerCase() !== currentUser.toLowerCase()) {
        hasOtherUserMessage = true;
      }
    });

    if (addedCount > 0 || isInitial) {
      if (loading) loading.classList.add('hidden');
      filterBubblesByChat();

      // Update snippet of general chat
      const last = allMessages[allMessages.length - 1];
      if (last) {
        const snippetText = document.getElementById('generalSnippetText');
        const lastTime = document.getElementById('generalLastTime');
        const parsed = parseMessageChannel(last.content);
        if (snippetText) snippetText.textContent = `${last.sender}: ${parsed.cleanText}`;
        if (lastTime && last.formattedTime) lastTime.textContent = last.formattedTime;
      }

      if (hasOtherUserMessage) {
        playNotificationChime();
        if (!isAtBottom) {
          const scrollBtn = document.getElementById('waScrollBottomBtn');
          if (scrollBtn) scrollBtn.classList.remove('hidden');
        }
      }

      if (isAtBottom || isInitial) {
        scrollToBottom();
      }
    }
  }

  function scrollToBottom() {
    const flow = document.getElementById('messagesFlow');
    const scrollBtn = document.getElementById('waScrollBottomBtn');
    if (flow) {
      flow.scrollTop = flow.scrollHeight;
    }
    if (scrollBtn) {
      scrollBtn.classList.add('hidden');
    }
  }

  // Load initial history
  async function loadInitialMessages() {
    try {
      const res = await fetch('/api/messages?limit=50');
      if (!res.ok) throw new Error('HTTP ' + res.status);
      const data = await res.json();
      const loading = document.getElementById('flowLoading');
      if (loading) loading.classList.add('hidden');

      if (data && data.length > 0) {
        appendMessages(data, true);
      }
    } catch (e) {
      const loading = document.getElementById('flowLoading');
      if (loading) {
        loading.innerHTML = '<span>Ready. Send a message to start.</span>';
      }
    }
  }

  // Poll new messages
  async function pollNewMessages() {
    try {
      const res = await fetch(`/api/messages/poll?afterId=${lastMessageId}`);
      if (!res.ok) return;
      const data = await res.json();
      if (data && data.length > 0) {
        appendMessages(data);
      }
    } catch (e) {}
  }

  // Fetch stats & update active members
  async function updateStats() {
    try {
      const res = await fetch('/api/stats');
      if (!res.ok) return;
      const data = await res.json();

      if (Array.isArray(data.activeUsers)) {
        renderMembersList(data.activeUsers);
      }
    } catch (e) {}
  }

  function renderMembersList(users) {
    const container = document.getElementById('dynamicMembersList');
    const countEl = document.getElementById('memberCountHeader');
    if (!container) return;

    const userSet = new Set(users);
    if (currentUser) userSet.add(currentUser);

    if (countEl) countEl.textContent = userSet.size;
    container.innerHTML = '';

    userSet.forEach(user => {
      const isMe = currentUser && (user.toLowerCase() === currentUser.toLowerCase());
      const item = document.createElement('div');
      item.className = 'chat-list-item ' + (activeChatId === user ? 'active' : '');
      item.dataset.chatId = user;
      item.dataset.name = user;
      item.dataset.type = 'direct';

      item.innerHTML = `
        <div class="item-avatar" style="background: ${getUserColor(user)}">
          ${escapeHtml(getInitials(user))}
        </div>
        <div class="item-details">
          <div class="item-header">
            <span class="item-name">${escapeHtml(user)} ${isMe ? '(You)' : ''}</span>
            <span class="item-time">online</span>
          </div>
          <div class="item-snippet">
            <span class="snippet-text">Click to message ${escapeHtml(user)}</span>
          </div>
        </div>
      `;

      item.addEventListener('click', () => switchChat(user, user));
      container.appendChild(item);
    });
  }

  // Send or Edit Message
  async function handleSendMessage(e) {
    if (e) e.preventDefault();
    const input = document.getElementById('composerInput');
    if (!input) return;

    const rawText = input.value.trim();
    if (!rawText) return;

    if (!currentUser) {
      document.getElementById('profileModal').classList.remove('hidden');
      return;
    }

    // If currently editing, dispatch update
    if (editingMessageId != null) {
      input.value = '';
      saveEditedMessage(editingMessageId, rawText);
      return;
    }

    // Standard new message
    const formattedContent = (activeChatId !== 'general-chat')
      ? `[${activeChatId}] ${rawText}`
      : rawText;

    input.value = '';
    const sendBtn = document.getElementById('composerSendBtn');
    if (sendBtn) sendBtn.classList.remove('can-send');

    // Create unique client tempId
    const tempId = 'temp-' + Date.now() + '-' + Math.random().toString(36).substr(2, 5);

    const optimisticMsg = {
      id: null,
      sender: currentUser,
      content: formattedContent,
      formattedTime: 'Just now',
      type: 'CHAT'
    };

    const feed = document.getElementById('bubblesFeed');
    const bubbleEl = createMessageElement(optimisticMsg, tempId);
    if (feed) feed.appendChild(bubbleEl);
    pendingTempIds.set(tempId, bubbleEl);
    scrollToBottom();

    try {
      const res = await fetch('/api/messages', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          sender: currentUser,
          content: formattedContent,
          type: 'CHAT'
        })
      });

      if (res.ok) {
        const saved = await res.json();
        if (saved && saved.id) {
          // Link element with real ID and timestamp
          bubbleEl.dataset.id = saved.id;
          delete bubbleEl.dataset.tempId;
          pendingTempIds.delete(tempId);

          const timeEl = bubbleEl.querySelector('.wa-time');
          if (timeEl && saved.formattedTime) timeEl.textContent = saved.formattedTime;

          renderedMessageIds.add(saved.id);
          lastMessageId = Math.max(lastMessageId, saved.id);
          allMessages.push(saved);
        }
      }

      // STOMP broadcast if active
      if (stompClient && stompClient.connected) {
        try {
          stompClient.send('/app/chat.sendMessage', {}, JSON.stringify({
            sender: currentUser,
            content: formattedContent,
            type: 'CHAT'
          }));
        } catch (ignored) {}
      }
    } catch (err) {
      showToast('Delivered to workspace');
    }
  }

  // In-Chat Search
  function handleInChatSearch(query) {
    const q = (query || '').toLowerCase().trim();
    const rows = document.querySelectorAll('.wa-message-row');
    rows.forEach(row => {
      if (!q) {
        row.style.display = 'flex';
        return;
      }
      const content = row.dataset.content || '';
      const sender = row.dataset.sender || '';
      if (content.includes(q) || sender.includes(q)) {
        row.style.display = 'flex';
      } else {
        row.style.display = 'none';
      }
    });
  }

  // Setup Event Listeners
  function initEvents() {
    initTheme();
    initContextMenu();

    // Cancel edit button
    const cancelEditBtn = document.getElementById('cancelEditBtn');
    if (cancelEditBtn) cancelEditBtn.addEventListener('click', cancelEditing);

    // Composer Form
    const form = document.getElementById('composerForm');
    const input = document.getElementById('composerInput');
    const sendBtn = document.getElementById('composerSendBtn');

    if (form) form.addEventListener('submit', handleSendMessage);
    if (sendBtn) sendBtn.addEventListener('click', handleSendMessage);

    if (input) {
      input.addEventListener('input', function () {
        if (this.value.trim().length > 0) {
          sendBtn.classList.add('can-send');
        } else {
          sendBtn.classList.remove('can-send');
        }
      });

      input.addEventListener('keydown', function (e) {
        if (e.key === 'Enter') {
          e.preventDefault();
          handleSendMessage();
        } else if (e.key === 'Escape' && editingMessageId != null) {
          cancelEditing();
        }
      });
    }

    // Default Group Chat Items
    document.querySelectorAll('.chat-list-item[data-chat-id]').forEach(item => {
      item.addEventListener('click', function () {
        const id = this.dataset.chatId;
        const name = this.dataset.name;
        switchChat(id, name);
      });
    });

    // Mobile Back Button
    const backBtn = document.getElementById('mobileBackBtn');
    if (backBtn) {
      backBtn.addEventListener('click', () => {
        const chatView = document.getElementById('chatView');
        if (chatView) chatView.classList.remove('open-mobile');
      });
    }

    // Search or start new chat
    const chatSearch = document.getElementById('chatSearchInput');
    const clearChatSearch = document.getElementById('clearChatSearch');
    if (chatSearch) {
      chatSearch.addEventListener('input', function () {
        const val = this.value.toLowerCase().trim();
        if (clearChatSearch) clearChatSearch.classList.toggle('hidden', val.length === 0);

        document.querySelectorAll('.chat-list-item').forEach(item => {
          const name = (item.dataset.name || '').toLowerCase();
          item.style.display = (!val || name.includes(val)) ? 'flex' : 'none';
        });
      });
    }
    if (clearChatSearch) {
      clearChatSearch.addEventListener('click', () => {
        chatSearch.value = '';
        chatSearch.dispatchEvent(new Event('input'));
        chatSearch.focus();
      });
    }

    // Filter Pills
    document.querySelectorAll('.filter-pills-row .pill-btn').forEach(btn => {
      btn.addEventListener('click', function () {
        document.querySelectorAll('.filter-pills-row .pill-btn').forEach(b => b.classList.remove('active'));
        this.classList.add('active');
        const filter = this.dataset.filter;

        document.querySelectorAll('.chat-list-item').forEach(item => {
          const type = item.dataset.type;
          if (filter === 'all') {
            item.style.display = 'flex';
          } else if (filter === 'groups') {
            item.style.display = (type === 'group') ? 'flex' : 'none';
          } else if (filter === 'members') {
            item.style.display = (type === 'direct') ? 'flex' : 'none';
          } else if (filter === 'unread') {
            const hasBadge = !item.querySelector('.item-badge')?.classList.contains('hidden');
            item.style.display = hasBadge ? 'flex' : 'none';
          }
        });
      });
    });

    // In-chat Search Toggle
    const searchInChatBtn = document.getElementById('searchInChatBtn');
    const inchatSearchBar = document.getElementById('inchatSearchBar');
    const inchatSearchInput = document.getElementById('inchatSearchInput');
    const closeInchatSearch = document.getElementById('closeInchatSearch');

    if (searchInChatBtn && inchatSearchBar) {
      searchInChatBtn.addEventListener('click', () => {
        inchatSearchBar.classList.toggle('hidden');
        if (!inchatSearchBar.classList.contains('hidden')) {
          inchatSearchInput.focus();
        } else {
          handleInChatSearch('');
        }
      });
    }
    if (inchatSearchInput) {
      inchatSearchInput.addEventListener('input', function () {
        handleInChatSearch(this.value);
      });
    }
    if (closeInchatSearch && inchatSearchBar) {
      closeInchatSearch.addEventListener('click', () => {
        inchatSearchBar.classList.add('hidden');
        inchatSearchInput.value = '';
        handleInChatSearch('');
      });
    }

    // Emojis Toggle
    const emojiToggle = document.getElementById('emojiToggleBtn');
    const emojiBar = document.getElementById('quickEmojisBar');
    if (emojiToggle && emojiBar) {
      emojiToggle.addEventListener('click', () => {
        emojiBar.classList.toggle('hidden');
      });
    }
    document.querySelectorAll('.quick-emoji').forEach(btn => {
      btn.addEventListener('click', function () {
        if (input) {
          input.value += this.dataset.emoji;
          input.focus();
          sendBtn.classList.add('can-send');
        }
      });
    });

    // Attachment Modal
    const attachBtn = document.getElementById('attachBtn');
    const attachModal = document.getElementById('attachModal');
    const closeAttach = document.getElementById('closeAttachModal');
    if (attachBtn && attachModal) {
      attachBtn.addEventListener('click', () => attachModal.classList.remove('hidden'));
    }
    if (closeAttach && attachModal) {
      closeAttach.addEventListener('click', () => attachModal.classList.add('hidden'));
    }
    document.querySelectorAll('.attach-option').forEach(opt => {
      opt.addEventListener('click', function () {
        const type = this.dataset.type;
        attachModal.classList.add('hidden');
        if (input) {
          if (type === 'doc') input.value += '📄 [Document] ';
          else if (type === 'photo') input.value += '📷 [Photo] ';
          else if (type === 'poll') input.value += '📊 [Poll] ';
          else if (type === 'contact') input.value += '👤 [Contact] ';
          input.focus();
          sendBtn.classList.add('can-send');
        }
      });
    });

    // Calls Modal
    const videoCallBtn = document.getElementById('videoCallBtn');
    const voiceCallBtn = document.getElementById('voiceCallBtn');
    const callModal = document.getElementById('callModal');
    const endCallBtn = document.getElementById('endCallBtn');
    const callTitle = document.getElementById('callTitle');

    function startCall(isVideo) {
      const name = document.getElementById('currentChatName')?.textContent || 'Contact';
      if (callTitle) callTitle.textContent = `${isVideo ? 'Video' : 'Voice'} Calling ${name}...`;
      if (callModal) callModal.classList.remove('hidden');
      playNotificationChime();
    }

    if (videoCallBtn) videoCallBtn.addEventListener('click', () => startCall(true));
    if (voiceCallBtn) voiceCallBtn.addEventListener('click', () => startCall(false));
    if (endCallBtn && callModal) endCallBtn.addEventListener('click', () => callModal.classList.add('hidden'));

    // Sound Toggle
    const soundBtn = document.getElementById('railSoundBtn');
    const soundIcon = document.getElementById('soundIcon');
    if (soundBtn) {
      soundBtn.addEventListener('click', () => {
        isSoundEnabled = !isSoundEnabled;
        localStorage.setItem('cloudify_sound', isSoundEnabled);
        if (soundIcon) {
          soundIcon.className = isSoundEnabled ? 'fa-solid fa-volume-high' : 'fa-solid fa-volume-xmark';
        }
        showToast(isSoundEnabled ? 'Sound alerts ON' : 'Sound alerts MUTED');
        if (isSoundEnabled) playNotificationChime();
      });
    }

    // Refresh Sync Button
    const syncBtn = document.getElementById('syncBtn');
    if (syncBtn) {
      syncBtn.addEventListener('click', () => {
        syncBtn.classList.add('spinning');
        pollNewMessages();
        updateStats();
        setTimeout(() => {
          syncBtn.classList.remove('spinning');
          showToast('Messages synced');
        }, 500);
      });
    }

    // Profile Modal
    const railProfile = document.getElementById('railProfileBtn');
    const profileModal = document.getElementById('profileModal');
    const closeProfile = document.getElementById('closeProfileModal');
    const profileForm = document.getElementById('profileForm');
    const nameInput = document.getElementById('profileNameInput');

    if (railProfile && profileModal) {
      railProfile.addEventListener('click', () => {
        if (nameInput) nameInput.value = currentUser;
        profileModal.classList.remove('hidden');
        if (nameInput) nameInput.focus();
      });
    }
    if (closeProfile && profileModal) {
      closeProfile.addEventListener('click', () => profileModal.classList.add('hidden'));
    }
    if (profileForm) {
      profileForm.addEventListener('submit', (e) => {
        e.preventDefault();
        const val = nameInput.value.trim();
        if (val) {
          applyUser(val);
          profileModal.classList.add('hidden');
          showToast(`Profile updated: ${val}`);
          updateStats();
        }
      });
    }

    // New Chat Button
    const newChatBtn = document.getElementById('newChatBtn');
    if (newChatBtn) {
      newChatBtn.addEventListener('click', () => {
        const target = prompt('Enter contact name:');
        if (target && target.trim()) {
          switchChat(target.trim(), target.trim());
        }
      });
    }

    // Scroll to bottom tracking
    const flow = document.getElementById('messagesFlow');
    const scrollBtn = document.getElementById('waScrollBottomBtn');
    if (flow) {
      flow.addEventListener('scroll', () => {
        const threshold = 50;
        const fromBottom = flow.scrollHeight - (flow.scrollTop + flow.clientHeight);
        isAtBottom = fromBottom < threshold;
        if (isAtBottom && scrollBtn) {
          scrollBtn.classList.add('hidden');
        }
      });
    }
    if (scrollBtn) {
      scrollBtn.addEventListener('click', scrollToBottom);
    }
  }

  // WebSocket Setup
  function initWebSocket() {
    if (typeof SockJS === 'undefined' || typeof Stomp === 'undefined') return;
    try {
      const socket = new SockJS('/ws-chat');
      stompClient = Stomp.over(socket);
      stompClient.debug = null;
      stompClient.connect({}, function () {
        stompClient.subscribe('/topic/public', function (output) {
          try {
            const body = JSON.parse(output.body);
            appendMessages([body]);
          } catch (e) {}
        });
      }, function () {});
    } catch (e) {}
  }

  // Boot
  function init() {
    checkUserSetup();
    initEvents();
    switchChat(activeChatId);
    loadInitialMessages();
    initWebSocket();
    updateStats();

    pollInterval = setInterval(pollNewMessages, 1500);
    statsInterval = setInterval(updateStats, 5000);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
