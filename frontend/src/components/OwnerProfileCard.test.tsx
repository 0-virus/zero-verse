import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { OwnerProfileCard } from './OwnerProfileCard'

describe('OwnerProfileCard', () => {
  it('should render owner nickname', () => {
    const mockOwner: any = {
      userId: 1,
      nickname: 'testblogger',
      profileImageUrl: 'https://example.com/avatar.jpg',
      bio: 'I write about tech',
    }

    render(<OwnerProfileCard owner={mockOwner} />)

    const nickname = screen.getByText('testblogger')
    expect(nickname).toBeDefined()
  })

  it('should render owner bio when provided', () => {
    const mockOwner: any = {
      userId: 1,
      nickname: 'testblogger',
      profileImageUrl: 'https://example.com/avatar.jpg',
      bio: 'I write about tech and science',
    }

    render(<OwnerProfileCard owner={mockOwner} />)

    const bio = screen.getByText('I write about tech and science')
    expect(bio).toBeDefined()
  })

  it('should render profile image when URL provided', () => {
    const mockOwner: any = {
      userId: 1,
      nickname: 'testblogger',
      profileImageUrl: 'https://example.com/avatar.jpg',
      bio: 'Blogger',
    }

    render(<OwnerProfileCard owner={mockOwner} />)

    const img = screen.getByAltText('testblogger') as HTMLImageElement
    expect(img).toBeDefined()
    expect(img.src).toBe('https://example.com/avatar.jpg')
  })

  it('should not render image when profileImageUrl is undefined', () => {
    const mockOwner: any = {
      userId: 1,
      nickname: 'testblogger',
      profileImageUrl: undefined,
      bio: 'Blogger',
    }

    render(<OwnerProfileCard owner={mockOwner} />)

    const nickname = screen.getByText('testblogger')
    expect(nickname).toBeDefined()

    // Should not have an image
    const images = screen.queryAllByRole('img')
    expect(images.length).toBe(0)
  })

  it('should render owner without bio', () => {
    const mockOwner: any = {
      userId: 2,
      nickname: 'simpleblogger',
      profileImageUrl: 'https://example.com/simple.jpg',
    }

    render(<OwnerProfileCard owner={mockOwner} />)

    const nickname = screen.getByText('simpleblogger')
    expect(nickname).toBeDefined()
  })

  it('should have correct container classes for styling', () => {
    const mockOwner: any = {
      userId: 1,
      nickname: 'testblogger',
      profileImageUrl: 'https://example.com/avatar.jpg',
      bio: 'Blogger',
    }

    const { container } = render(<OwnerProfileCard owner={mockOwner} />)

    const cardDiv = container.querySelector('.bg-bg-panel')
    expect(cardDiv).toBeDefined()
    expect(cardDiv?.classList.contains('border-2')).toBe(true)
    expect(cardDiv?.classList.contains('border-border-purple-dark')).toBe(true)
  })
})
