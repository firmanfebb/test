// =====================
//  Notepad Todolist App
// =====================

let tasks = JSON.parse(localStorage.getItem('tasks')) || [];
let currentFilter = 'all';

// --- DOM Elements ---
const taskInput   = document.getElementById('taskInput');
const addBtn      = document.getElementById('addBtn');
const taskList    = document.getElementById('taskList');
const taskCount   = document.getElementById('taskCount');
const clearBtn    = document.getElementById('clearCompleted');
const filterBtns  = document.querySelectorAll('.filter-btn');

// --- Save to localStorage ---
function saveTasks() {
  localStorage.setItem('tasks', JSON.stringify(tasks));
}

// --- Add Task ---
function addTask() {
  const text = taskInput.value.trim();
  if (!text) {
    taskInput.focus();
    taskInput.style.borderColor = '#e05555';
    setTimeout(() => taskInput.style.borderColor = '', 1000);
    return;
  }

  const task = {
    id: Date.now(),
    text: text,
    completed: false
  };

  tasks.push(task);
  saveTasks();
  renderTasks();
  taskInput.value = '';
  taskInput.focus();
}

// --- Delete Task ---
function deleteTask(id) {
  tasks = tasks.filter(t => t.id !== id);
  saveTasks();
  renderTasks();
}

// --- Toggle Complete ---
function toggleTask(id) {
  const task = tasks.find(t => t.id === id);
  if (task) {
    task.completed = !task.completed;
    saveTasks();
    renderTasks();
  }
}

// --- Edit Task ---
function editTask(id) {
  const task = tasks.find(t => t.id === id);
  if (!task) return;

  const newText = prompt('Edit tugas:', task.text);
  if (newText === null) return; // user batal
  const trimmed = newText.trim();
  if (!trimmed) return;

  task.text = trimmed;
  saveTasks();
  renderTasks();
}

// --- Clear Completed ---
function clearCompleted() {
  tasks = tasks.filter(t => !t.completed);
  saveTasks();
  renderTasks();
}

// --- Filter ---
function setFilter(filter) {
  currentFilter = filter;
  filterBtns.forEach(btn => {
    btn.classList.toggle('active', btn.dataset.filter === filter);
  });
  renderTasks();
}

// --- Get Filtered Tasks ---
function getFilteredTasks() {
  switch (currentFilter) {
    case 'active':    return tasks.filter(t => !t.completed);
    case 'completed': return tasks.filter(t => t.completed);
    default:          return tasks;
  }
}

// --- Render Tasks ---
function renderTasks() {
  const filtered = getFilteredTasks();
  taskList.innerHTML = '';

  if (filtered.length === 0) {
    taskList.innerHTML = `<li class="empty-state">✨ Tidak ada tugas di sini</li>`;
  } else {
    filtered.forEach(task => {
      const li = document.createElement('li');
      li.className = `task-item${task.completed ? ' completed' : ''}`;
      li.innerHTML = `
        <input type="checkbox" ${task.completed ? 'checked' : ''} 
               onchange="toggleTask(${task.id})" title="Tandai selesai"/>
        <span class="task-text">${escapeHtml(task.text)}</span>
        <div class="task-actions">
          <button class="edit-btn" onclick="editTask(${task.id})" title="Edit">✏️</button>
          <button class="delete-btn" onclick="deleteTask(${task.id})" title="Hapus">🗑️</button>
        </div>
      `;
      taskList.appendChild(li);
    });
  }

  // Update counter
  const remaining = tasks.filter(t => !t.completed).length;
  taskCount.textContent = `${remaining} tugas tersisa`;
}

// --- Escape HTML (security) ---
function escapeHtml(text) {
  const div = document.createElement('div');
  div.appendChild(document.createTextNode(text));
  return div.innerHTML;
}

// --- Event Listeners ---
addBtn.addEventListener('click', addTask);

taskInput.addEventListener('keydown', (e) => {
  if (e.key === 'Enter') addTask();
});

clearBtn.addEventListener('click', clearCompleted);

filterBtns.forEach(btn => {
  btn.addEventListener('click', () => setFilter(btn.dataset.filter));
});

// --- Init ---
renderTasks();
