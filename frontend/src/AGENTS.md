<!-- Parent: ../../../AGENTS.md -->

# ZeroVerse Frontend (`frontend/src/`) — Development Notes

**Scope**: Frontend scaffolding for M0 (Vite + React 19 + TypeScript + Tailwind v4).

## Architecture Overview

### Folder Structure

```
src/
├── main.tsx, App.tsx         // Entry + router setup (AuthProvider + RouterProvider)
├── routes/                   // Router definition + guards
│  ├── router.tsx             // Route objects
│  ├── ProtectedRoute.tsx     // Requires login
│  ├── GuestOnlyRoute.tsx     // Signin/signup only
│  ├── AdminRoute.tsx         // Requires ADMIN role
│  └── SetupGuard.tsx         // Requires blog setup complete
├── pages/                    // Screen components (one per route)
│  ├── MainPage, SigninPage, SignupPage, BlogInitialSetupPage
│  ├── BlogPage, PostDetailPage, WritePage, EditPage
│  ├── SettingsProfilePage, SettingsUniversePage, SettingsPostsPage
│  ├── SearchPage, NotificationsPage, AdminPage, AdminUserPage
├── features/                 // Domain-specific hooks/API (TODO: expand in M1+)
├── components/
│  ├── layout/                // AppShell, TopBar, SideNav
│  └── ui/                    // Badge, Button, Card, Table, Modal, etc. (stubs)
├── lib/
│  ├── authContext.tsx        // AuthContext + useAuth hook
│  └── apiClient.ts           // Fetch wrapper: Bearer auth, 401 refresh, response unwrap
├── styles/
│  └── index.css              // Tailwind v4 + design tokens + custom shadows
└── types/                    // API DTO interfaces
```

### Key Decisions (Align with PRD §9)

| Item | Value | Notes |
|------|-------|-------|
| **Design System** | PRD §6 colors/shadows/fonts | Tailwind v4 with CSS custom properties |
| **Auth State** | AuthContext (memory + httponly) | Access Token in memory, Refresh in cookie |
| **API Client** | Custom fetch wrapper | 401 → auto-refresh → retry (single-flight) |
| **Visibility Enum** | `UNIVERSE` (display "친구") | PRD §9-B |
| **Category Types** | `DEFAULT/GENERAL/LOCKED` | PRD §9-H (SERIES removed) |
| **Sidebar** | Present on Main/Settings/Search/Admin; absent on Blog/Post/Write/Auth | PRD §6.6 |
| **Responsive** | Desktop only (1440px baseline) | PRD §9-I |

### Design Tokens (Tailwind Theme)

**Colors**:
- bg: `#02020b` (space), `#050512` (navbar), `#070817` (panel), `#030712` (input), `#0f1029` (tab), `#111827` (button-neutral), `#0e7490` (button-primary), `#3b0718` (button-danger)
- border: cyan `#22d3ee`/`#67e8f9`/`#38bdf8`, purple `#a855f7`/`#a78bfa`, danger `#fb7185`, admin `#fde047`
- text: primary `#f8fafc`, muted `#94a3b8`, body-cyan `#e0f2fe`, tab `#d8b4fe`

**Shadows** (hard offset, no blur):
- card `4px 4px 0 rgba(49,46,129,0.82)`
- button `3px 3px 0 rgba(88,28,135,0.9)`
- search `3px 3px 0 rgba(14,116,144,0.7)`
- navbar `0 4px 0 rgba(30,27,75,0.95)`
- glow `0 0 10px rgba(34,211,238,0.28),4px 4px 0 rgba(0,0,0,0.8)`

**Fonts**:
- Logo/heading: `Press Start 2P`
- Body/UI: `IBM Plex Sans KR`

### Route Map (PRD §7.1)

| Path | Component | Auth | Sidebar | Purpose |
|------|-----------|------|---------|---------|
| `/` | MainPage | optional | yes | Feed (universe/public) |
| `/signin` | SigninPage | guest | — | Login form |
| `/signup` | SignupPage | guest | — | Register form |
| `/blog/setup` | BlogInitialSetupPage | required | — | Initial setup flow |
| `/blog/:blogSlug` | BlogPage | optional | — | Public blog view |
| `/blog/:blogSlug/:postId` | PostDetailPage | optional | — | Post + comments |
| `/write` | WritePage | required | — | Create post (TipTap stub) |
| `/edit/:postId` | EditPage | required | — | Edit post |
| `/settings` | SettingsProfilePage | required | yes | Profile/blog/security |
| `/settings/universe` | SettingsUniversePage | required | yes | Relationship mgmt |
| `/settings/posts` | SettingsPostsPage | required | yes | Category tree CRUD |
| `/search` | SearchPage | optional | yes | Unified search |
| `/notifications` | NotificationsPage | required | — | Notification center |
| `/admin` | AdminPage | admin | yes | User list |
| `/admin/users/:userId` | AdminUserPage | admin | yes | User detail + actions |

### API Client Usage

```typescript
// Setup in App.tsx
<AuthProvider>
  <RouterProvider router={router} />
</AuthProvider>

// In components
const { user, accessToken, signin, signout, refreshAccessToken } = useAuth()

// API calls
const response = await apiClient('/auth/signin', {
  method: 'POST',
  body: JSON.stringify({ email, password }),
})
// On 401: auto-refresh, retry once, or redirect to /signin
// Response shape: { success, data?, error?, timestamp }
```

### Page Stub Behavior

Each page is a **layout-only stub**:
- **No fake API success responses** — pages show "Loading..." or placeholder text.
- **Form validation** — inputs exist but don't call actual endpoints (M1+ will wire).
- **Navigation** — links work; guards redirect appropriately.
- **AppShell usage** — pages use `<AppShell showSidebar={true|false}>` for consistent layout.

### Common Patterns

1. **Protected routes**: Wrap in `<ProtectedRoute>`, `<AdminRoute>`, `<GuestOnlyRoute>`, `<SetupGuard>`.
2. **Form submission**: Collect state, call `apiClient()`, handle response.
3. **Async operations**: Use `useState(isLoading)` or simple `try/catch` (no React Query yet).
4. **Style classes**: Use `@apply` + theme vars, custom shadows via CSS classes.
5. **Fonts**: Use `.font-display` (Press Start 2P) for headings, default body is IBM Plex Sans KR.

### M1+ TODO

- AuthContext: Integrate `setAccessToken()` from apiClient when token is refreshed.
- Pages: Remove "mock" placeholders, connect to real APIs.
- UI components: Implement `Badge`, `Button` variants, `Table`, `Modal`, `Pagination`, `TagInput`, `FormField`.
- Features: Add domain-specific hooks (usePost, useBlog, useUniverse, etc.) in `src/features/`.
- Testing: Vitest + React Testing Library for route rendering, AppShell smoke tests.

### Debug/Development

```bash
cd frontend
npm run dev          # Start dev server (localhost:5173)
npm run build        # Production build to dist/
npm run lint         # Run oxlint
```

Environment: `VITE_API_BASE_URL` (default `http://localhost:8080`).

---

**Last updated**: 2026-07-01 (M0 scaffolding complete)
