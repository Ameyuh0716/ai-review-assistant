import { get, post, put, del } from './request'

export interface Course {
  id: number
  name: string
  description?: string
  userId: number
  createdAt?: string
  updatedAt?: string
}

export function listCourses() {
  return get<Course[]>('/api/courses')
}

export function getCourse(id: number) {
  return get<Course>(`/api/courses/${id}`)
}

export function createCourse(course: Partial<Course>) {
  return post<Course>('/api/courses', course)
}

export function updateCourse(id: number, course: Partial<Course>) {
  return put<Course>(`/api/courses/${id}`, course)
}

export function deleteCourse(id: number) {
  return del<boolean>(`/api/courses/${id}`)
}
