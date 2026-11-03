import { formError, friendlyMessage } from '../../Utils/errors';
import { AppError } from '../../Models/HttpModels';
import { InlineAlert } from '../CustomUI';

export function FormError({ error }: { error: AppError | null | undefined }) {
  const shown = formError(error);
  return shown ? <InlineAlert tone="danger">{friendlyMessage(shown)}</InlineAlert> : null;
}
