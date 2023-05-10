import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AppCardComponent } from './app-card.component';

describe('AppCardComponent', () => {
    let component: AppCardComponent;
    let fixture: ComponentFixture<AppCardComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [AppCardComponent]
        });
        fixture = TestBed.createComponent(AppCardComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
