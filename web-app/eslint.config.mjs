import eslint from '@eslint/js';
import eslintConfigPrettier from 'eslint-config-prettier';
import globals from 'globals';
import tseslint from 'typescript-eslint';

export default [
	{
		ignores: ['dist/**', 'node_modules/**', 'public/mockServiceWorker.js', 'vite.config.mts']
	},
	eslint.configs.recommended,
	...tseslint.configs.recommended,
	eslintConfigPrettier,
	{
		languageOptions: {
			globals: globals.browser
		},
		rules: {
			'@typescript-eslint/no-unused-vars': 'warn',
			'no-unused-vars': 'off',
			'no-console': ['error', { allow: ['warn', 'error'] }],
			'no-tabs': 'off',
			quotes: ['error', 'single', { avoidEscape: true }],
			semi: ['error', 'never'],
			'jsx-quotes': ['error', 'prefer-double']
		}
	}
];
