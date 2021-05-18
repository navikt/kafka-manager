import { toast } from 'react-toastify';

export function errorToast(message: string) {
	toast(message, { type: 'error', position: 'top-right' });
}

export function successToast(message: string) {
	toast(message, { type: 'success', position: 'top-right' });
}

export function infoToast(message: string) {
	toast(message, { type: 'info', position: 'top-right' });
}

export function warningToast(message: string) {
	toast(message, { type: 'warning', position: 'top-right' });
}
