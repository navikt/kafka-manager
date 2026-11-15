interface UnsafeComboboxMockProps {
    label: string
    options: string[]
    selectedOptions?: string[]
    onToggleSelected: (option: string, isSelected?: boolean) => void
    isLoading?: boolean
}

export const UnsafeComboboxMock = ({
    label,
    options,
    selectedOptions,
    onToggleSelected,
    isLoading
}: UnsafeComboboxMockProps) => {
    return (
        <label>
            {label}
            <select
                aria-label={label}
                value={selectedOptions?.[0] ?? ''}
                onChange={event => {
                    const option = event.currentTarget.value
                    onToggleSelected(option, Boolean(option))
                }}
            >
                <option value=""/>
                {options.map(option => (
                    <option key={option} value={option}>
                        {option}
                    </option>
                ))}
            </select>
            {isLoading && <span>Loading</span>}
        </label>
    )
}
