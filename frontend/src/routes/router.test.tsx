import { describe, it, expect } from 'vitest'
import { RouterProvider } from 'react-router-dom'
import { render } from '@testing-library/react'
import router from './router'

describe('Router', () => {
  it('should render main page on root route', () => {
    router.navigate('/')
    const { container } = render(<RouterProvider router={router} />)
    expect(container).toBeDefined()
  })

  it('should render signin page on /signin route', () => {
    router.navigate('/signin')
    const { container } = render(<RouterProvider router={router} />)
    expect(container).toBeDefined()
  })
})
