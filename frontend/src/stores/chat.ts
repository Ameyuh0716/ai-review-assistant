import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface CourseContext {
  id: number
  name: string
}

export const useChatStore = defineStore('chat', () => {
  const currentCourse = ref<CourseContext | null>(null)

  function setCourse(course: CourseContext | null) {
    currentCourse.value = course
    if (course) {
      localStorage.setItem('currentCourseId', String(course.id))
      localStorage.setItem('currentCourseName', course.name)
    } else {
      localStorage.removeItem('currentCourseId')
      localStorage.removeItem('currentCourseName')
    }
  }

  function loadCourseFromStorage() {
    const id = localStorage.getItem('currentCourseId')
    const name = localStorage.getItem('currentCourseName')
    if (id && name) {
      currentCourse.value = { id: parseInt(id), name }
    }
  }

  return {
    currentCourse,
    setCourse,
    loadCourseFromStorage
  }
})
