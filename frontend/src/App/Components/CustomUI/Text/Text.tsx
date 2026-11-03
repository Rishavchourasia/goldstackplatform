import type { ElementType, ReactNode } from 'react';
import { cx } from '../../../Utils/cx';
import styles from './Text.module.scss';

export type TextVariant =
  'display' | 'h1' | 'h2' | 'h3' | 'body' | 'bodyStrong' | 'small' | 'caption' | 'overline' | 'mono';
export type TextTone = 'default' | 'secondary' | 'muted' | 'accent' | 'success' | 'danger';

const DEFAULT_ELEMENT: Record<TextVariant, ElementType> = {
  display: 'p',
  h1: 'h1',
  h2: 'h2',
  h3: 'h3',
  body: 'p',
  bodyStrong: 'p',
  small: 'p',
  caption: 'span',
  overline: 'span',
  mono: 'span',
};

interface TextProps {
  variant?: TextVariant;
  tone?: TextTone;

  as?: ElementType;
  align?: 'start' | 'center' | 'end';

  tabular?: boolean;
  className?: string;
  children: ReactNode;
  id?: string;
}

export function Text({ variant = 'body', tone = 'default', as, align, tabular, className, children, id }: TextProps) {
  const Element = as ?? DEFAULT_ELEMENT[variant];
  return (
    <Element
      id={id}
      className={cx(
        styles[variant],
        styles[`tone-${tone}`],
        align && styles[`align-${align}`],
        tabular && 'tabular',
        className,
      )}
    >
      {children}
    </Element>
  );
}
