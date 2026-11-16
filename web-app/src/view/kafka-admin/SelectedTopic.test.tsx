import {render, screen} from '@testing-library/react'
import {describe, expect, it} from 'vitest'
import {SelectedTopic} from './SelectedTopic'

describe('SelectedTopic layout', () => {
    it('shows the selected topic', () => {
        render(<SelectedTopic selectedTopic="events-topic"/>)

        expect(screen.getByText('events-topic')).toBeTruthy()
    })

    it('shows a placeholder when no topic is selected', () => {
        render(<SelectedTopic selectedTopic={null}/>)

        expect(screen.getByText('No topic selected')).toBeTruthy()
    })
})
