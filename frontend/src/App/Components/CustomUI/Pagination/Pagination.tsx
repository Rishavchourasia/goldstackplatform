import { Button } from '../Button/Button';
import { Text } from '../Text/Text';
import styles from './Pagination.module.scss';

export function Pagination({
  page,
  totalPages,
  onChange,
}: {
  page: number;
  totalPages: number;
  onChange: (p: number) => void;
}) {
  if (totalPages <= 1) return null;
  return (
    <nav className={styles.pager} aria-label="Pagination">
      <Button size="sm" variant="ghost" disabled={page === 0} onClick={() => onChange(page - 1)}>
        ← Newer
      </Button>
      <Text variant="small" tone="muted">
        Page {page + 1} of {totalPages}
      </Text>
      <Button size="sm" variant="ghost" disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)}>
        Older →
      </Button>
    </nav>
  );
}
