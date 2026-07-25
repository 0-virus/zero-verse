/** 디자인 정본 §7.2. */
export interface TabItem {
  key: string;
  label: string;
}

export interface TabsProps {
  items: TabItem[];
  activeKey: string;
  onChange: (key: string) => void;
}

export function Tabs({ items, activeKey, onChange }: TabsProps) {
  return (
    <div role="tablist" className="flex gap-2">
      {items.map((item) => {
        const active = item.key === activeKey;
        return (
          <button
            key={item.key}
            type="button"
            role="tab"
            aria-selected={active}
            onClick={() => onChange(item.key)}
            className={`cursor-pointer border-[3px] border-ink px-5 py-2 text-[13px] font-bold shadow-btn ${
              active ? 'bg-ink text-text-on-ink' : 'bg-surface text-ink hover:bg-surface-raise'
            }`}
          >
            {item.label}
          </button>
        );
      })}
    </div>
  );
}
