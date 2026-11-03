import type { ReactNode } from 'react';
import { cx } from '../../../Utils/cx';
import styles from './DataTable.module.scss';

export interface Column<T> {
  header: string;

  cell: (row: T) => ReactNode;

  numeric?: boolean;
}

export function DataTable<T>({
  columns,
  rows,
  rowKey,
  caption,
}: {
  columns: ReadonlyArray<Column<T>>;
  rows: ReadonlyArray<T>;
  rowKey: (row: T) => string;
  caption?: string;
}) {
  return (
    <div className={styles.wrap}>
      <table className={styles.table}>
        {caption && <caption className="sr-only">{caption}</caption>}
        <thead>
          <tr>
            {columns.map((c) => (
              <th key={c.header} className={cx(c.numeric && styles.num)}>
                {c.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, i) => (
            <tr key={rowKey(row)} style={{ animationDelay: `${Math.min(i, 10) * 25}ms` }}>
              {columns.map((c) => (
                <td key={c.header} className={cx(c.numeric && styles.num)}>
                  {c.cell(row)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
